(ns rems.db.catalogue
  (:require [clojure.string :as str]))

(defn urn-catalogue-item? [{:keys [resid]}]
  (and resid (str/starts-with? resid "http://urn.fi")))

(defn get-catalogue-item-title [item language]
  (or (get-in item [:localizations language :title])
      (:title item)))

(defn disabled-catalogue-item? [item]
  (= (:state item) "disabled"))
