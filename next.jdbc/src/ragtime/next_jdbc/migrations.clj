(ns ragtime.next-jdbc.migrations
  (:require ; [clojure.java.jdbc :as jdbc]
            [ragtime.next-jdbc :as ragtime]))

(defn create-table [id table specs]
  (ragtime/sql-migration
   {:id   id
    :up   [(str "create table " table " ( " specs " ) ")]
    :down [(str "drop table " table )]}))
