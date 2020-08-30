(ns ragtime.next-jdbc.migrations
  (:require
    [clojure.string :as str]
    [ragtime.next-jdbc :as ragtime]))


(defn specs->string [specs]
  (->> specs
       (map (fn [[n type]]
              (str (name n) " " type)))
       (str/join ", ")))


(defn create-table [id table specs]
  (ragtime/sql-migration
    {:id   id
     :up   [(str "CREATE TABLE " (name table) " (" (specs->string specs) ")")]
     :down [(str "DROP TABLE " (name table))]}))
