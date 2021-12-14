(ns rems.tree
  ;; TODO ns documentation
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [rems.atoms :refer [checkbox sort-symbol]]
            [rems.common.util :refer [index-by]]
            [rems.guide-util :refer [component-info example namespace-info]]
            [rems.search :as search]
            [rems.text :refer [text]]
            [schema.core :as s]))

;; TODO implement schema for the parameters

(defn apply-row-defaults [tree row expanded]
  (let [children ((:children tree :children) row)]
    (merge
     ;; row defaults
     {:key ((:key tree :key) row)
      :children children
      :depth (:depth row 0)
      :value (dissoc row :depth)}

     ;; column defaults
     {:columns (->> (:columns tree)
                    (map-indexed (fn [i column]
                                   (let [first-column? (= i 0)
                                         value (if-let [value-fn (:value column (:key column))]
                                                 (value-fn row)
                                                 (get row (:key column)))
                                         display-value (str value)]
                                     (merge {:sort-value (if (string? value)
                                                           (str/lower-case value)
                                                           value)
                                             :display-value display-value
                                             :filter-value (str/lower-case display-value)
                                             :td (when-let [content (if (:content column)
                                                                      ((:content column) row)
                                                                      [:div display-value])]
                                                   (if-let [td-fn (:td column)]
                                                     (td-fn row)

                                                     [:td {:class [(name (:key column))
                                                                   (str "bg-depth-" (:depth row 0))]
                                                           :col-span (when-let [col-span-fn (:col-span column)] (col-span-fn row))}
                                                      [:div.d-flex.flex-row.w-100.align-items-baseline
                                                       {:class [(when first-column? (str "pad-depth-" (:depth row 0)))
                                                                (when expanded "expanded")]}

                                                       (when first-column?
                                                         (when (seq children)
                                                           (if expanded
                                                             [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-up]
                                                             [:i.pl-1.pr-4.fas.fa-fw.fa-chevron-down])))

                                                       content]]))}
                                            (dissoc column :td :col-span))))))}

     ;; overrides
     (select-keys row [:id :key :sort-value :display-value :filter-value :td :tr-class]))))

(rf/reg-sub
 ::flattened-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe (:rows tree))
         expanded-rows @(rf/subscribe [::expanded-rows tree])]

     (loop [flattened []
            rows rows]
       (if (empty? rows)
         flattened
         (let [row (first rows)
               expanded? (contains? expanded-rows ((:key tree :key) row)) ; slightly unelegant to have this as parameter to row defaults
               row (apply-row-defaults tree row expanded?)
               depth (:depth row)
               new-depth (inc depth)
               children (mapv #(assoc % :depth new-depth) (:children row))]
           (if (and expanded? (seq children))
             (recur (into flattened [row])
                    (into (vec children) (rest rows)))
             (recur (into flattened [row])
                    (rest rows)))))))))

(rf/reg-sub
 ::displayed-rows
 (fn [db [_ tree]]
   (let [rows @(rf/subscribe [::flattened-rows tree])]
     rows)))

(defn- set-toggle [set key]
  (let [set (or set #{})]
    (if (contains? set key)
      (disj set key)
      (conj set key))))

(rf/reg-event-db
 ::toggle-row-expanded
 (fn [db [_ tree key]]
   (let [new-db (update-in db [::expanded-rows (:id tree)] set-toggle key)]
     (when-let [on-expand (:on-expand tree)]
       (on-expand (get-in new-db [::expanded-rows (:id tree)])))
     new-db)))

(rf/reg-sub
 ::expanded-row
 (fn [db [_ tree key]]
   (contains? (get-in db [::expanded-rows (:id tree)]) key)))

(rf/reg-sub
 ::expanded-rows
 (fn [db [_ tree]]
   (get-in db [::expanded-rows (:id tree)])))

(rf/reg-event-db
 ::set-expanded-rows
 (fn [db [_ tree rows]]
   (let [expanded-rows (set (map :key rows))
         new-db (assoc-in db [::expanded-rows (:id tree)] expanded-rows)]
     (when-let [on-select (:on-select tree)]
       (on-select expanded-rows))
     new-db)))

(defn- table-header [tree]
  (into [:tr]
        (for [column (:columns tree)]
          [:th (:title column)])))

(defn- table-row [row tree]
  (into [:tr {:data-row (:key row)
              :class [(when-let [tr-class-fn (:tr-class tree)]
                        (tr-class-fn (:value row)))
                      (when (seq (:children row))
                        :clickable)]
              :on-click (when (seq (:children row))
                          #(rf/dispatch [::toggle-row-expanded tree (:key row)]))}]
        (mapv :td (:columns row))))

(defn tree [tree]
  (let [rows @(rf/subscribe [::displayed-rows tree])
        language @(rf/subscribe [:language])]

    [:div.table-border
     [:table.rems-table {:id (name (:id tree))
                         :class (:id tree)}
      [:thead
       [table-header tree]]
      [:tbody {:key language} ; performance optimization: rebuild instead of update existing components
       (for [row rows]
         ^{:key (:key row)}
         [table-row row tree])]]]))



;;; guide

(def example-selected-rows (reagent/atom nil))

(defn guide []
  [:div
   (namespace-info rems.tree)
   (component-info tree)

   (example "empty tree"
            (rf/reg-sub ::empty-table-rows (fn [_ _] []))

            [tree {:id ::example0
                   :columns [{:key :first-name
                              :title "First name"
                              :sortable? false
                              :filterable? false}
                             {:key :last-name
                              :title "Last name"
                              :sortable? false
                              :filterable? false}]
                   :rows [::empty-table-rows]
                   :default-sort-column :first-name}])

   (example "setup example data"
            (defn- example-commands [text]
              [:div.commands [:button.btn.btn-primary {:on-click #(do (js/alert (str "View " text)) (.stopPropagation %))} "View"]])

            (def example-data
              [{:key 0
                :category {:title "Users"}
                :children [{:key 1
                            :category {:title "Applicants"}
                            :commands (example-commands "Applicants")}
                           {:key 2
                            :category {:title "Handlers"}
                            :commands (example-commands "Handlers")}
                           {:key 3
                            :category {:title "Administration"}
                            :commands (example-commands "Administration")
                            :children [{:key 4
                                        :category {:title "Reporters"}
                                        :commands (example-commands "Reporters")}
                                       {:key 5
                                        :category {:title "Owners"}
                                        :commands (example-commands "Owners")
                                        :children [{:key 6
                                                    :category {:title "Super Owners"}
                                                    :commands (example-commands "Super owners")}
                                                   {:key 7
                                                    :category {:title "Org Owners"}
                                                    :commands (example-commands "Org Owners")}]}]}]
                :commands (example-commands "Users")}])

            (rf/reg-sub ::example-tree-rows (fn [_ _] example-data)))

   (example "static tree with three rows"
            [tree {:id ::example1
                   :columns [{:key :category
                              :title "Name"
                              :value (comp :title :category)
                              :sortable? false
                              :filterable? false}
                             {:key :commands
                              :content :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::example-tree-rows]
                   :default-sort-column :title}])

   ;; TODO implement selection if needed
   #_(example "tree with selectable rows"
              [:p "The tree components supports selection of rows. You can provide a callback for when the set of selected rows changes."]
              [:div [:p "You have " (count @example-selected-rows) " rows selected."]]
              [tree {:id ::example-selectable
                     :columns [{:key :first-name
                                :title "First name"
                                :sortable? false
                                :filterable? false}
                               {:key :last-name
                                :title "Last name"
                                :sortable? false
                                :filterable? false}
                               {:key :commands
                                :sortable? false
                                :filterable? false}]
                     :rows [::example-tree-rows]
                     :default-sort-column :first-name
                     :selectable? true
                     :on-select #(reset! example-selected-rows %)}])

   ;; TODO implement sorting
   ;; TODO implement filtering
   #_(example "sortable and filterable tree"
              [:p "Filtering and search can be added by using the " [:code "rems.table/search"] " component"]
              (let [example2 {:id ::example2
                              :columns [{:key :first-name
                                         :title "First name"}
                                        {:key :last-name
                                         :title "Last name"}
                                        {:key :commands
                                         :sortable? false
                                         :filterable? false}]
                              :rows [::example-tree-rows]
                              :default-sort-column :first-name}]
                [:div
                 [search example2]
                 [tree example2]]))])
