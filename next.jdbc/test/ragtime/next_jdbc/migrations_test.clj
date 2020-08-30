(ns ragtime.next-jdbc.migrations-test
  (:require [clojure.test :refer :all]
            [ragtime.next-jdbc :as jdbc]
            [ragtime.next-jdbc.migrations :refer :all]))

(deftest create-table-test
  (is (= (create-table ::foo :foo [[:id "int"] [:name "varchar(255)"]])
         (jdbc/sql-migration
          {:id   ::foo
           :up   ["CREATE TABLE foo (id int, name varchar(255))"]
           :down ["DROP TABLE foo"]}))))
