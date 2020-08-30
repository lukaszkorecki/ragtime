(ns ragtime.next-jdbc-test
  (:require [clojure.test :refer :all]
            [ragtime.next-jdbc :as next-jdbc]
            [ragtime.core :as core]
            [ragtime.protocols :as p]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(def db-spec "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")

(use-fixtures :each (fn reset-db [f]
                      (jdbc/execute! db-spec ["DROP ALL OBJECTS"])
                      (f)))

(deftest test-add-migrations
  (let [db (next-jdbc/sql-database db-spec)]
    (p/add-migration-id db "12")
    (p/add-migration-id db "13")
    (p/add-migration-id db "20")
    (is (= ["12" "13" "20"] (p/applied-migration-ids db)))
    (p/remove-migration-id db "13")
    (is (= ["12" "20"] (p/applied-migration-ids db)))))

(deftest test-migrations-table
  (let [db (next-jdbc/sql-database db-spec
                              {:migrations-table "migrations"})]
    (p/add-migration-id db "12")
    (is (= ["12"]
           (map :MIGRATIONS/ID
           (sql/query (:db-spec db) ["SELECT * FROM migrations"] {:row-fn :id})))))

  (jdbc/execute! db-spec ["CREATE SCHEMA myschema"])
  (let [db (next-jdbc/sql-database db-spec
                              {:migrations-table "myschema.migrations"})]
    (p/add-migration-id db "20")
    (p/add-migration-id db "21")
    (is (= ["20" "21"]
           (map :MIGRATIONS/ID
           (sql/query (:db-spec db) ["SELECT * FROM myschema.migrations"] {:row-fn :MIGRATIONS/ID}))))))

(defn table-names [db]
  (set (map :TABLES/TABLE_NAME (sql/query (:db-spec db) ["SHOW TABLES"] {:row-fn :table_name}))))

(defn test-sql-migration [db-spec migration-extras]
  (let [db (next-jdbc/sql-database db-spec)
        m  (next-jdbc/sql-migration
            (merge {:id   "01"
                    :up   ["CREATE TABLE foo (id int)"]
                    :down ["DROP TABLE foo"]}
                   migration-extras))]
    (core/migrate db m)
    (is (= #{"RAGTIME_MIGRATIONS" "FOO"} (table-names db)))
    (core/rollback db m)
    (is (= #{"RAGTIME_MIGRATIONS"} (table-names db)))))

(deftest test-sql-migration-using-db-spec
  (test-sql-migration db-spec {}))

(deftest test-sql-migration-without-transaction
  (test-sql-migration db-spec { :transactions false }))

(deftest test-sql-migration-with-up-transaction
  (test-sql-migration db-spec { :transactions :up }))

(deftest test-sql-migration-with-down-transaction
  (test-sql-migration db-spec { :transactions :down }))

(deftest test-sql-migration-with-both-transaction
  (test-sql-migration db-spec { :transactions :both })
  (test-sql-migration db-spec { :transactions true }))

(deftest test-sql-migration-using-db-spec-with-existing-connection
  (with-open [conn (jdbc/get-connection db-spec)]
    (test-sql-migration conn {})))

(deftest test-load-directory
  (let [db  (next-jdbc/sql-database db-spec)
        ms  (next-jdbc/load-directory "test/migrations")
        idx (core/into-index ms)]
    (core/migrate-all db idx ms)
    (is (= #{"RAGTIME_MIGRATIONS" "FOO" "BAR" "BAZ" "QUZA" "QUZB" "QUXA" "QUXB" "LAST_TABLE"}
           (table-names db)))
    (is (= ["001-test" "002-bar" "003-test" "004-test" "005-test" "006-test"]
           (p/applied-migration-ids db)))
    (core/rollback-last db idx (count ms))
    (is (= #{"RAGTIME_MIGRATIONS"} (table-names db)))
    (is (empty? (p/applied-migration-ids db)))))

(deftest test-load-resources
  (let [db  (next-jdbc/sql-database db-spec)
        ms  (next-jdbc/load-resources "migrations")
        idx (core/into-index ms)]
    (core/migrate-all db idx ms)
    (is (= #{"RAGTIME_MIGRATIONS" "FOO" "BAR" "BAZ" "QUZA" "QUZB" "QUXA" "QUXB" "LAST_TABLE"}
           (table-names db)))
    (is (= ["001-test" "002-bar" "003-test" "004-test" "005-test" "006-test"]
           (p/applied-migration-ids db)))
    (core/rollback-last db idx (count ms))
    (is (= #{"RAGTIME_MIGRATIONS"} (table-names db)))
    (is (empty? (p/applied-migration-ids db)))))

(deftest test-migration-ordering
  (let [ids   (for [i (range 10000)] (format "%04d-test" i))
        files (mapcat (fn [id] [(str id ".up.sql") (str id ".down.sql")]) ids)]
    (with-redefs [file-seq (constantly (map io/file (shuffle files)))
                  slurp    (constantly "SELECT 1;")]
      (let [migrations (next-jdbc/load-directory "foo")]
        (is (= (count migrations) 10000))
        (is (= (map :id migrations) ids))))))
