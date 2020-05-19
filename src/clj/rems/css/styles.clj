(ns rems.css.styles
  "CSS stylesheets are generated by garden automatically when
  accessing the application on a browser. The garden styles can also
  be manually compiled by calling the function `rems.css.styles/screen-css`.

  For development purposes with Figwheel the styles are rendered to
  `target/resources/public/css/:language/screen.css` whenever this ns is evaluated
  so that Figwheel can autoreload them."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [garden.color :as c]
            [garden.core :as g]
            [garden.def :refer [defkeyframes]]
            [garden.selectors :as s]
            [garden.stylesheet :as stylesheet]
            [garden.units :as u]
            [medley.core :refer [map-vals remove-vals]]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.context :as context]))

(defn get-theme-attribute
  "Fetch the attribute value from the current theme with fallbacks.

  Keywords denote attribute lookups while strings are interpreted as fallback constant value."
  [& attr-names]
  (when (seq attr-names)
    (let [attr-name (first attr-names)
          attr-value (if (keyword? attr-name)
                       (get (:theme env) attr-name)
                       attr-name)]
      (or attr-value (recur (rest attr-names))))))

(def content-width (u/px 1200))

(defn resolve-image [path]
  (when path
    (let [url (if (str/starts-with? path "http")
                path
                (str (get-theme-attribute :img-path "../../img/") path))]
      (str "url(\"" url "\")"))))

(defn get-logo-image [lang]
  (resolve-image (get-theme-attribute (keyword (str "logo-name-" (name lang))) :logo-name)))

(defn get-logo-name-sm [lang]
  (resolve-image (get-theme-attribute (keyword (str "logo-name-" (name lang) "-sm")) :logo-name-sm)))


(defn- generate-at-font-faces []
  (list
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Light.eot')"}
                            {:src "url('/font/Lato-Light.eot') format('embedded-opentype'), url('/font/Lato-Light.woff2') format('woff2'), url('/font/Lato-Light.woff') format('woff'), url('/font/Lato-Light.ttf') format('truetype')"
                             :font-weight 300
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Regular.eot')"}
                            {:src "url('/font/Lato-Regular.eot') format('embedded-opentype'), url('/font/Lato-Regular.woff2') format('woff2'), url('/font/Lato-Regular.woff') format('woff'), url('/font/Lato-Regular.ttf') format('truetype')"
                             :font-weight 400
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Lato'"
                             :src "url('/font/Lato-Bold.eot')"}
                            {:src "url('/font/Lato-Bold.eot') format('embedded-opentype'), url('/font/Lato-Bold.woff2') format('woff2'), url('/font/Lato-Bold.woff') format('woff'), url('/font/Lato-Bold.ttf') format('truetype')"
                             :font-weight 700
                             :font-style "normal"})
   (stylesheet/at-font-face {:font-family "'Roboto Slab'"
                             :src "url('/font/Roboto.woff2') format('woff2')"
                             :font-weight 400
                             :font-style "normal"})))

(defn- generate-form-placeholder-styles []
  (list
   [".form-control::placeholder" {:color "#ccc"}] ; Standard
   [".form-control::-webkit-input-placeholder" {:color "#ccc"}] ; WebKit, Blink, Edge
   [".form-control:-moz-placeholder" {:color "#ccc"
                                      :opacity 1}] ; Mozilla Firefox 4 to 18
   [".form-control::-moz-placeholder" {:color "#ccc"
                                       :opacity 1}] ; Mozilla Firefox 19+
   [".form-control:-ms-input-placeholder" {:color "#ccc"}])) ; Internet Explorer 10-11

(defn- generate-media-queries []
  (list
   (stylesheet/at-media {:max-width (u/px 480)}
                        (list
                         [(s/descendant :.rems-table.cart :tr)
                          {:border-bottom "none"}]
                         [(s/descendant :.logo :.img)
                          {:background-color (get-theme-attribute :logo-bgcolor)
                           :background-image (get-logo-name-sm context/*lang*)
                           :-webkit-background-size :contain
                           :-moz-background-size :contain
                           :-o-background-size :contain
                           :background-size :contain
                           :background-repeat :no-repeat
                           :background-position [[:center :center]]}]
                         [:.logo
                          {:height (u/px 150)}]))
   (stylesheet/at-media {:max-width (u/px 870)}
                        [:.user-widget [:.icon-description {:display "none"}]])
   (stylesheet/at-media {:min-width (u/px 480)}
                        [:.commands {:white-space "nowrap"}])))

(defn- generate-phase-styles []
  [:.phases {:width "100%"
             :height (u/px 40)
             :display "flex"
             :flex-direction "row"
             :justify-content "stretch"
             :align-items "center"}
   [:.phase {:background-color (get-theme-attribute :phase-bgcolor "#eee")
             :color (get-theme-attribute :phase-color "#111")
             :flex-grow 1
             :height (u/px 40)
             :display "flex"
             :flex-direction "row"
             :justify-content "stretch"
             :align-items "center"}
    [:span {:flex-grow 1
            :text-align "center"
            :min-width (u/px 100)}]
    [(s/& ":not(:last-of-type):after") {:content "\"\""
                                        :border-top [[(u/px 20) :solid :white]]
                                        :border-left [[(u/px 10) :solid :transparent]]
                                        :border-bottom [[(u/px 20) :solid :white]]
                                        :border-right "none"}]
    [(s/& ":first-of-type") {:border-top-left-radius (u/px 4)
                             :border-bottom-left-radius (u/px 4)}]
    [(s/& ":last-of-type") {:border-top-right-radius (u/px 4)
                            :border-bottom-right-radius (u/px 4)}]
    [(s/& ":not(:first-of-type):before") {:content "\"\""
                                          :border-top [[(u/px 20) :solid :transparent]]
                                          :border-left [[(u/px 10) :solid :white]]
                                          :border-bottom [[(u/px 20) :solid :transparent]]
                                          :border-right "none"}]
    [:&.active (merge {:color (get-theme-attribute :phase-color-active :phase-color "#111")}
                      (if-let [background (get-theme-attribute :phase-background-active)]
                        {:background background}
                        {:background-color (get-theme-attribute :phase-bgcolor-active "#ccc")
                         :border-color (get-theme-attribute :phase-bgcolor-active "#ccc")}))]
    [:&.completed {:background-color (get-theme-attribute :phase-bgcolor-completed "#ccc")
                   :border-color (get-theme-attribute :phase-bgcolor-completed "#ccc")
                   :color (get-theme-attribute :phase-color-completed :phase-color)}]]])

(defn- button-navbar-font-weight []
  ;; Default font-weight to 700 so the text is considered
  ;; 'large text' and thus requires smaller contrast ratio for
  ;; accessibility.
  (get-theme-attribute :button-navbar-font-weight 700))

(defn table-selection-bgcolor []
  (if-let [selection-bgcolor (get-theme-attribute :table-selection-bgcolor)]
    selection-bgcolor
    (-> (get-theme-attribute :table-hover-bgcolor :table-bgcolor :color3 "#777777")
        (c/darken 15))))

(defn- generate-rems-table-styles []
  (list
   [:.rems-table.cart {:background "#fff"
                       :color "#000"
                       :margin 0}
    [".cart-bundle:not(:last-child)" {:border-bottom [[(u/px 1) :solid (get-theme-attribute :color1)]]}]
    [:td:before {:content "initial"}]
    [:th
     :td:before
     {:color "#000"}]
    [:tr
     [(s/& (s/nth-child "2n")) {:background "#fff"}]]]
   [:#event-table
    {:white-space "pre-wrap"}
    [:.date {:min-width "160px"}]]
   [:.table-border {:padding 0
                    :margin "1em 0"
                    :border (get-theme-attribute :table-border "1px solid #ccc")
                    :border-radius (u/rem 0.4)}]
   [:.rems-table {:min-width "100%"
                  :background-color (get-theme-attribute :table-bgcolor :color1)
                  :box-shadow (get-theme-attribute :table-shadow)
                  :color (get-theme-attribute :table-text-color)}
    [:th {:white-space "nowrap"
          :color (get-theme-attribute :table-heading-color "#fff")
          :background-color (get-theme-attribute :table-heading-bgcolor :color3)}]
    [:th
     :td
     {:text-align "left"
      :padding "0.5em 1em"}]
    [:.selection {:width (u/rem 0.5)
                  :padding-right 0}]
    [:td:before
     {:color (get-theme-attribute :table-text-color)}]
    [:tr {:margin "0 1rem"}
     [:&:hover {:color (get-theme-attribute :table-hover-color :table-text-color "#fff")
                :background-color (get-theme-attribute :table-hover-bgcolor :color2)}]
     [:&.selected {:background-color (get-theme-attribute :table-selection-bgcolor (table-selection-bgcolor))}]
     [(s/& (s/nth-child "2n"))
      [:&:hover {:color (get-theme-attribute :table-hover-color :table-text-color "#fff")
                 :background-color (get-theme-attribute :table-hover-bgcolor :color2)}]
      {:background-color (get-theme-attribute :table-stripe-color :table-bgcolor :color1)}
      [:&.selected {:background-color (get-theme-attribute :table-selection-bgcolor (table-selection-bgcolor))}]]]
    [:td.commands:last-child {:text-align "right"
                              :padding-right (u/rem 1)}]]
   [:.rems-table.cart {:box-shadow :none}]
   [:.inner-cart {:margin (u/em 1)}]
   [:.outer-cart {:border [[(u/px 1) :solid (get-theme-attribute :color1)]]
                  :border-radius (u/rem 0.4)}]
   [:.cart-title {:margin-left (u/em 1)
                  :font-weight "bold"}]
   [:.cart-item {:padding-right (u/em 1)}
    [:>span {:display :inline-block :vertical-align :middle}]]
   ;; TODO: Change naming of :color3? It is used as text color here,
   ;;   which means that it should have a good contrast with light background.
   ;;   This could be made explicit by changing the name accordingly.
   [:.text-highlight {:color (get-theme-attribute :color3)
                      :font-weight "bold"}]))

(def ^:private dashed-form-group
  {:position "relative"
   :border "2px dashed #ccc"
   :border-radius (u/rem 0.4)
   :padding (u/px 10)
   :margin-top 0
   :margin-bottom (u/px 16)})

(defn- remove-nil-vals
  "Recursively removes all keys with nil values from a map."
  [obj]
  (assert (not= "" obj))
  (cond
    (record? obj) obj
    (map? obj) (->> obj
                    (map-vals remove-nil-vals)
                    (remove-vals nil?)
                    not-empty)
    (vector? obj) (mapv remove-nil-vals obj)
    (seq? obj) (map remove-nil-vals obj)
    :else obj))

(defn- make-important [style]
  (map-vals #(str % " !important") style))

(deftest test-remove-nil-vals
  (testing "empty"
    (is (= nil
           (remove-nil-vals {}))))
  (testing "flat"
    (is (= nil
           (remove-nil-vals {:a nil})))
    (is (= {:a 1}
           (remove-nil-vals {:a 1})))
    (is (= {:a false}
           (remove-nil-vals {:a false})))
    (is (= {:a "#fff"}
           (remove-nil-vals {:a "#fff"}))))
  (testing "nested"
    (is (= nil
           (remove-nil-vals {:a {:b nil}})))
    (is (= {:a {:b 1}}
           (remove-nil-vals {:a {:b 1}}))))
  (testing "multiple keys"
    (is (= {:b 2}
           (remove-nil-vals {:a nil
                             :b 2}))))
  (testing "vectors"
    (is (vector? (remove-nil-vals [1])))
    (is (= []
           (remove-nil-vals [])))
    (is (= [:a]
           (remove-nil-vals [:a])))
    (is (= [:a nil]
           (remove-nil-vals [:a {}])))
    (is (= [:a {:b 1}]
           (remove-nil-vals [:a {:b 1}])))
    (is (= [:a nil]
           (remove-nil-vals [:a {:b nil}]))))
  (testing "lists"
    (is (seq? (remove-nil-vals '(1))))
    (is (= '()
           (remove-nil-vals '())))
    (is (= '(:a)
           (remove-nil-vals '(:a))))
    (is (= '(:a nil)
           (remove-nil-vals '(:a {})))))
  (testing "records"
    (is (= (u/px 10)
           (remove-nil-vals (u/px 10)))))
  (testing "empty strings are not supported"
    ;; CSS should not contain empty strings, but to be sure
    ;; that we don't accidentally break stuff, we don't convert
    ;; them to nil but instead throw an error.
    (is (thrown? AssertionError (remove-nil-vals {:a ""})))))

(defkeyframes shake
  ["10%, 90%"
   {:transform "perspective(500px) translate3d(0, 0, 1px)"}]
  ["20%, 80%"
   {:transform "perspective(500px) translate3d(0, 0, -3px)"}]
  ["30%, 50%, 70%"
   {:transform "perspective(500px) translate3d(0, 0, 8px)"}]
  ["40%, 60%"
   {:transform "perspective(500px) translate3d(0, 0, -8px)"}])

(defkeyframes pulse-opacity
  ["0%"
   {:opacity "1.0"}]
  ["100%"
   {:opacity "0.0"}])

(defn build-screen []
  (list
   (generate-at-font-faces)
   [:* {:margin 0}]
   [:a
    :button
    {:cursor :pointer
     :color (get-theme-attribute :link-color "#007bff")}
    [:&:hover {:color (get-theme-attribute :link-hover-color :color4)}]]
   [:.pointer {:cursor :pointer}
    [:label.form-check-label {:cursor :pointer}]]
   [:html {:position :relative
           :min-width (u/px 320)
           :height (u/percent 100)}]
   [:body {:font-family (get-theme-attribute :font-family "'Lato', sans-serif")
           :min-height (u/percent 100)
           :display :flex
           :flex-direction :column
           :padding-top (u/px 56)}]
   [:h1 :h2 {:font-weight 400}]
   [:h1 {:margin-bottom (u/rem 2)}]
   [:#app {:min-height (u/percent 100)
           :flex 1
           :display :flex}]
   [(s/> :#app :div) {:min-height (u/percent 100)
                      :flex 1
                      :display :flex
                      :flex-direction :column}]
   [:.fixed-top {:background-color "#fff"
                 :border-bottom (get-theme-attribute :header-border "3px solid #ccc")
                 :box-shadow (get-theme-attribute :header-shadow :table-shadow)
                 :min-height (u/px 56)}]
   [:.skip-navigation {:position :absolute
                       :left (u/em -1000)}
    [:&:active
     :&:focus
     {:left (u/em 0)}]]
   [:#main-content {:display :flex
                    :flex-direction :column
                    :flex-wrap :none
                    :min-height (u/px 300)
                    :max-width content-width
                    :flex-grow 1
                    ;; Height of navigation + logo, to avoid page content going under
                    ;; the navigation bar when the main content is focused.
                    ;; See https://stackoverflow.com/questions/4086107/fixed-page-header-overlaps-in-page-anchors
                    :padding-top (u/px 212)
                    :margin-top (u/px -212)}]
   [(s/> :.spaced-sections "*:not(:first-child)") {:margin-top (u/rem 1)}]
   [:.btn {:white-space :nowrap
           :font-weight (button-navbar-font-weight)}]
   ;; Bootstrap has inaccessible focus indicators in particular
   ;; for .btn-link and .btn-secondary, so we define our own.
   [:a:focus :button:focus :.btn.focus :.btn:focus
    {:outline 0
     :box-shadow "0 0 0 0.2rem rgba(38,143,255,.5) !important"}]
   [:.btn-primary
    [:&:hover
     :&:focus
     :&:active:hover
     {:background-color (get-theme-attribute :primary-button-hover-bgcolor :primary-button-bgcolor :color4)
      :border-color (get-theme-attribute :primary-button-hover-bgcolor :primary-button-bgcolor :color4)
      :color (get-theme-attribute :primary-button-hover-color :primary-button-color "#fff")
      :outline-color :transparent}]
    {:background-color (get-theme-attribute :primary-button-bgcolor :color4)
     :border-color (get-theme-attribute :primary-button-bgcolor :color4)
     :color (get-theme-attribute :primary-button-color "#fff")
     :outline-color :transparent}]
   [:.btn-secondary
    ;; Only override bootstrap's default if the key is defined in the theme
    [:&:hover
     :&:focus
     :&:active:hover
     (into {}
           (filter val
                   {:background-color (get-theme-attribute :secondary-button-hover-bgcolor)
                    :border-color (get-theme-attribute :secondary-button-hover-bgcolor)
                    :color (get-theme-attribute :secondary-button-hover-color)
                    :outline-color :transparent}))]
    (into {}
          (filter val
                  {:background-color (get-theme-attribute :secondary-button-bgcolor)
                   :border-color (get-theme-attribute :secondary-button-bgcolor)
                   :color (get-theme-attribute :secondary-button-color)
                   :outline-color :transparent}))]
   [:.btn-primary.disabled :.btn-primary:disabled ; same color as bootstrap's default for .btn-secondary.disabled
    {:color "#fff"
     :background-color "#6c757d"
     :border-color "#6c757d"}]
   [:.button-min-width {:min-width (u/rem 5)}]
   [:.icon-link {:color "#6c757d" ; same color as bootstrap's default for .btn-secondary.disabled
                 :cursor "pointer"}
    [:&:hover {:color "#5a6268"}]]
   [:.icon-stack-background {:color "white"
                             :font-size "110%"}]
   [:.modal--title [:.link
                    {:border-radius "0.25em"
                     :padding "0.25em"
                     :text-align :center
                     :color "#ccc"}
                    [:&:hover {:color (get-theme-attribute :color4)
                               :background-color "#eee"}]]]
   [:.flash-message-title {:font-weight :bold}]

   [:.text-primary {:color (get-theme-attribute :text-primary)}]
   [:.text-secondary {:color (get-theme-attribute :text-secondary)}]
   [:.text-success {:color (get-theme-attribute :text-success)}]
   [:.text-danger {:color (get-theme-attribute :text-danger)}]
   [:.text-warning {:color (get-theme-attribute :text-warning "#ffc107!important")}]
   [:.text-info {:color (get-theme-attribute :text-info)}]
   [:.text-light {:color (get-theme-attribute :text-light)}]
   [:.text-dark {:color (get-theme-attribute :text-dark)}]
   [:.text-muted {:color (get-theme-attribute :text-muted)}]
   [:.text-white {:color (get-theme-attribute :text-white)}]

   [:.bg-primary {:background-color (get-theme-attribute :bg-primary)}]
   [:.bg-secondary {:background-color (get-theme-attribute :bg-secondary)}]
   [:.bg-success {:background-color (get-theme-attribute :bg-success)}]
   [:.bg-danger {:background-color (get-theme-attribute :bg-danger)}]
   [:.bg-warning {:background-color (get-theme-attribute :bg-warning)}]
   [:.bg-info {:background-color (get-theme-attribute :bg-info)}]
   [:.bg-light {:background-color (get-theme-attribute :bg-light)}]
   [:.bg-dark {:background-color (get-theme-attribute :bg-dark)}]
   [:.bg-white {:background-color (get-theme-attribute :bg-white)}]

   [:.alert-primary {:color (get-theme-attribute :alert-primary-color)
                     :background-color (get-theme-attribute :alert-primary-bgcolor)
                     :border-color (get-theme-attribute :alert-primary-bordercolor :alert-primary-color)}]
   [:.alert-secondary {:color (get-theme-attribute :alert-secondary-color)
                       :background-color (get-theme-attribute :alert-secondary-bgcolor)
                       :border-color (get-theme-attribute :alert-secondary-bordercolor :alert-secondary-color)}]
   [:.alert-success
    (s/descendant :.state-approved.phases :.phase.completed)
    (s/descendant :.state-submitted.phases :.phase.completed)
    {:color (get-theme-attribute :alert-success-color)
     :background-color (get-theme-attribute :alert-success-bgcolor)
     :border-color (get-theme-attribute :alert-success-bordercolor :alert-success-color)}]
   [:.alert-danger
    :.state-rejected
    :.state-revoked
    (s/descendant :.state-rejected.phases :.phase.completed)
    (s/descendant :.state-revoked.phases :.phase.completed)
    {:color (get-theme-attribute :alert-danger-color)
     :background-color (get-theme-attribute :alert-danger-bgcolor)
     :border-color (get-theme-attribute :alert-danger-bordercolor :alert-danger-color)}]
   [:.alert-warning {:color (get-theme-attribute :alert-warning-color)
                     :background-color (get-theme-attribute :alert-warning-bgcolor)
                     :border-color (get-theme-attribute :alert-warning-bordercolor :alert-warning-color)}]
   [:.alert-info
    {:color (get-theme-attribute :alert-info-color)
     :background-color (get-theme-attribute :alert-info-bgcolor)
     :border-color (get-theme-attribute :alert-info-bordercolor :alert-info-color)}]
   [:.alert-light {:color (get-theme-attribute :alert-light-color)
                   :background-color (get-theme-attribute :alert-light-bgcolor)
                   :border-color (get-theme-attribute :alert-light-bordercolor :alert-light-color)}]
   [:.alert-dark {:color (get-theme-attribute :alert-dark-color)
                  :background-color (get-theme-attribute :alert-dark-bgcolor)
                  :border-color (get-theme-attribute :alert-dark-bordercolor :alert-dark-color)}]
   shake
   [:.flash-message.alert-danger
    {:animation [[shake "0.6s cubic-bezier(.36,.07,.19,.97) both"]]}]

   ;; animating opacity instead of box-shadow for smooth performance
   ;; https://tobiasahlin.com/blog/how-to-animate-box-shadow/
   pulse-opacity
   [".flash-message.alert-success::after"
    {:content "''"
     :position :absolute
     :border-radius ".25rem"
     :top 0
     :left 0
     :width "100%"
     :height "100%"
     :box-shadow "0 0 4px 8px rgba(60, 108, 61, 0.5)"
     :animation [[pulse-opacity "0.6s ease-out 1 both"]]}]

   ;; Navbar
   [:.navbar-wrapper
    {:max-width content-width}]
   [:.navbar
    {:font-size (u/px 19)
     :letter-spacing (u/rem 0.015)
     :padding-left 0
     :padding-right 0
     :color (get-theme-attribute :navbar-color "#111")}
    [:.nav-link :.btn-link
     {:background-color :inherit}]]
   [:.navbar-top-bar {:width (u/percent 100)
                      :height (u/px 4)
                      :display :flex
                      :flex-direction :row}]
   [:.navbar-top-left {:flex 1
                       :background-color (get-theme-attribute :color4)}]
   [:.navbar-top-right {:flex 1
                        :background-color (get-theme-attribute :color2)}]
   [:.navbar-text {:font-size (u/px 19)
                   :font-weight (button-navbar-font-weight)}]
   [:.navbar-toggler {:border-color (get-theme-attribute :color1)}]
   [:.nav-link
    :.btn-link
    {:color (get-theme-attribute :nav-color :link-color :color3)
     :font-weight (button-navbar-font-weight)
     :border 0} ; for button links
    [:&.active
     {:color (get-theme-attribute :nav-active-color :color4)}]
    [:&:hover
     {:color (get-theme-attribute :nav-hover-color :color4)}]]
   [:.navbar {:white-space "nowrap"}]
   [(s/descendant :.user-widget :.nav-link) {:display :inline-block}]
   [:.user-name {:text-transform :none}]
   [:#big-navbar {:text-transform (get-theme-attribute :big-navbar-text-transform "none")}]
   [(s/descendant :.navbar-text :.language-switcher)
    {:margin-right (u/rem 1)}]
   [:.navbar-flex {:display "flex"
                   :flex-direction "row"
                   :justify-content "space-between"
                   :min-width "100%"}]
   ;; Footer
   (let [footer-text-color (get-theme-attribute :footer-color :table-heading-color "#fff")]
     [:footer {:width "100%"
               :min-height (u/px 53.6)
               :color footer-text-color
               :font-size (u/px 19) ;; same as navbar
               :padding-top "1rem"
               :padding-bottom "1rem"
               :background-color (get-theme-attribute :footer-bgcolor :table-heading-bgcolor :color3)
               :margin-top (u/em 1)}
      [:a :a:hover {:color footer-text-color
                    :font-weight (button-navbar-font-weight)}]
      [:.dev-reload-button {:float "right"}]])

   ;; Logo, login, etc.
   [:.logo {:height (u/px 140)
            :background-color (get-theme-attribute :logo-bgcolor)
            :padding "0 20px"
            :margin-bottom (u/em 1)}]
   [(s/descendant :.logo :.img) {:height "100%"
                                 :background-color (get-theme-attribute :logo-bgcolor)
                                 :background-image (get-logo-image context/*lang*)
                                 :-webkit-background-size :contain
                                 :-moz-o-background-size :contain
                                 :-o-background-size :contain
                                 :background-size :contain
                                 :background-repeat :no-repeat
                                 :background-position [[:center :center]]
                                 :background-origin (get-theme-attribute :logo-content-origin)
                                 :padding-left (u/px 20)
                                 :padding-right (u/px 20)
                                 :padding-top (u/px 8)}]
   [:.jumbotron
    {:background-color "#fff"
     :text-align "center"
     :color "#000"
     :margin-top (u/rem 2)
     :border-style "solid"
     :border-width (u/px 1)
     :box-shadow (get-theme-attribute :collapse-shadow :table-shadow)}
    [:h1 {:margin-bottom (u/px 20)}]]
   [:.login-btn {:max-height (u/px 70)
                 :margin-bottom (u/px 20)}
    [:&:hover {:filter "brightness(80%)"}]]

   (generate-rems-table-styles)
   [:.btn.disabled {:opacity 0.25}]
   [:.catalogue-item-link {:color "#fff"
                           :text-decoration "underline"}]
   [:.language-switcher {:padding ".5em 0"}]
   (generate-media-queries)
   [:.example-page {:margin (u/rem 2)}]
   [(s/> :.example-page :h1) {:margin "4rem 0"}]
   [(s/> :.example-page :h2) {:margin-top (u/rem 8)
                              :margin-bottom (u/rem 2)}]
   [(s/> :.example-page :h3) {:margin-bottom (u/rem 1)}]
   [(s/descendant :.example-page :.example) {:margin-bottom (u/rem 4)}]
   [:.example-content {:border "1px dashed black"}]
   [:.example-content-end {:clear "both"}]
   [:textarea.form-control {:overflow "hidden"}
    ;; XXX: Override the browser's default validation for textarea that has
    ;;   the attribute 'required': If a required element is invalid, do not
    ;;   show the box shadow, which is used at least by Firefox to highlight
    ;;   the invalid element. If the element is also in focus, show
    ;;   Bootstrap's default box shadow instead.
    [:&:required:invalid {:-webkit-box-shadow :none}
     [:&:focus {:-webkit-box-shadow "0 0 0 .2rem rgba(0,123,255,.25)"}]]]
   [:.group {:position "relative"
             :border "1px solid #ccc"
             :border-radius (u/rem 0.4)
             :padding (u/px 10)
             :margin-top 0
             :margin-bottom (u/px 16)}]
   [:div.form-control {:height :auto
                       :white-space "pre-wrap"
                       :border-color "rgba(206, 212, 218, 0.2)" ; "#ced4da"
                       :background-color "rgba(0, 0, 0, 0.01)"}
    [:&:empty {:height (u/rem 2.25)}]]
   [:.toggle-diff {:float "right"}]
   [:.diff
    [:ins {:background-color "#acf2bd"}]
    [:del {:background-color "#fdb8c0"}]]
   [:form.inline
    :.form-actions.inline
    {:display :inline-block}
    [:.btn-link
     {:border :none
      :padding 0}]]
   [:.modal-title {:color "#292b2c"}]
   [(s/+
     (s/descendant :.language-switcher :form)
     :form)
    {:margin-left (u/rem 0.5)}]
   [:.commands {:text-align "right"
                :padding "0 1rem"
                :cursor :auto}]
   [".spaced-horizontally > *:not(:first-child)" {:margin-left (u/rem 0.5)}]
   [".spaced-vertically > *:not(:first-child)" {:margin-top (u/rem 0.5)}]
   [".spaced-vertically-3 > *:not(:first-child)" {:margin-top (u/rem 1.5)}]
   [".children-inline-blocks > *" {:display :inline-block}]

   [(s/> :.form-actions "*:not(:first-child)")
    (s/> :.commands "*:not(:first-child)")
    {:margin-left (u/em 0.5)}]
   [".btn-opens-more::after" {:content "'...'"}]

   [:#action-commands {:display "flex"
                       :flex-flow "row wrap"
                       :margin-bottom (u/em -0.5)}
    [(s/> "*")
     {:margin-bottom (u/em 0.5)}]
    [(s/> "*:not(:last-child)")
     {:margin-right (u/em 0.5)}]]

   ;; form inputs
   ["input[type=date].form-control" {:width (u/em 12)}]
   [:.form-group {:text-align "initial"}
    ;; make fieldset legends look the same as normal labels
    [:legend {:font-size "inherit"}]]
   [:#application-fields
    [:.application-field-label {:font-weight "bold"}]]

   ;; custom checkbox
   [:.readonly-checkbox {:background-color "#ccc"}]

   [:.dashed-group dashed-form-group]

   ;; workflow editor
   [:.workflow-round dashed-form-group
    [:h2 {:font-weight 400
          :font-size (u/rem 1.4)}]]
   [:.next-workflow-arrow {:position "absolute"
                           :font-size (u/px 40)
                           :left (u/percent 50)
                           :transform "translate(-50%, -1%)"
                           :z-index 1}]
   [:.new-workflow-round {:text-align "center"}]
   [:.remove-workflow-round {:float "right"}]

   ;; form editor
   [:#main-content.page-create-form {:max-width :unset}]
   [:.form-field dashed-form-group]
   [:.form-field-header {:margin-bottom (u/rem 0.5)}
    [:h4 {:display "inline"
          :font-weight "bold"
          :font-size (u/rem 1.1)}]]
   [:.form-field-controls {:float "right"}
    [:* {:margin-left (u/em 0.25)}]]
   [:.new-form-field {:text-align "center"}]

   [:.form-field-visibility (assoc dashed-form-group
                               :margin-left 0
                               :margin-right 0)]
   [:.form-field-option (assoc dashed-form-group
                               :margin-left 0
                               :margin-right 0)]
   [:.new-form-field-option {:text-align "center"}]

   [:#preview-form {:position :sticky ;; TODO seems to work on Chrome and Firefox. check Edge?
                    :top "100px"}
    [:.collapse-content {:margin-left 0}]
    [:#preview-form-contents {:overflow-y :scroll
                              :overflow-x :hidden
                              ;; subtract #preview-form top value plus a margin here to stay inside the viewbox
                              :max-height "calc(100vh - 220px)"}]]

   [:.field-preview {:position :relative
                     :margin-left (u/rem 1)}]
   [:.full {:width "100%"}]
   [:.intro {:margin-bottom (u/rem 2)}]
   [:.rectangle {:width (u/px 50)
                 :height (u/px 50)}]
   [:.color-1 {:background-color (get-theme-attribute :color1)}]
   [:.color-2 {:background-color (get-theme-attribute :color2)}]
   [:.color-3 {:background-color (get-theme-attribute :color3)}]
   [:.color-4 {:background-color (get-theme-attribute :color4)}]
   [:.color-title {:padding-top (u/rem 0.8)}]
   [(s/descendant :.alert :ul) {:margin-bottom 0}]
   [:ul.comments {:list-style-type :none}]
   [:.inline-comment {:font-size (u/rem 1)}]
   [(s/& :p.inline-comment ":last-child") {:margin-bottom 0}]
   [:.inline-comment-content {:display :inline-block}]
   [:.license-panel {:display :inline-block
                     :width "inherit"}]
   [:.clickable {:cursor :pointer}]
   [:.rems-card-margin-fix {:margin (u/px -1)}] ; make sure header overlaps container border
   [:.rems-card-header {:color (get-theme-attribute :table-heading-color "#fff")
                        :background-color (get-theme-attribute :table-heading-bgcolor :color3)}]
   [(s/descendant :.card-header :a) {:color :inherit}]
   [:.application-resources
    [:.application-resource {:margin-bottom (u/rem 1)
                             :line-height (u/rem 1)
                             :font-size (u/rem 1)}]]
   [:.license {:margin-bottom (u/rem 1)}
    [:.license-block {:color "#000"
                      :white-space "pre-wrap"}]
    [:.license-title {;; hax for opening misalignment
                      :margin-top (u/px 3)
                      :line-height (u/rem 1)
                      :font-size (u/rem 1)}]]
   [:.collapsing {:-webkit-transition "height 0.1s linear"
                  :-o-transition "height 0.1s linear"
                  :transition "height 0.1s linear"}]
   [:.collapse-toggle {:text-align :center}]
   [:.collapse-wrapper {:border-radius (u/rem 0.4)
                        :border "1px solid #ccc"
                        :background-color (get-theme-attribute :collapse-bgcolor "#fff")
                        :box-shadow (get-theme-attribute :collapse-shadow :table-shadow)}
    [:.card-header {:border-bottom "none"
                    :border-radius (u/rem 0.4)
                    :font-weight 400
                    :font-size (u/rem 1.5)
                    :line-height 1.1
                    :font-family (get-theme-attribute :font-family "'Lato', sans-serif")
                    :color (get-theme-attribute :collapse-color "#fff")}
     [:&.alert-danger {:color (get-theme-attribute :danger-color "inherit")}]]]
   [:.collapse-content {:margin (u/rem 1.25)}]
   [:.collapse-wrapper.slow
    [:.collapsing {:-webkit-transition "height 0.25s linear"
                   :-o-transition "height 0.25s linear"
                   :transition "height 0.25s linear"}]]

   [:.color1 {:color (get-theme-attribute :color1)}]
   [:.color1-faint {:color (when (get-theme-attribute :color1)
                             (-> (get-theme-attribute :color1)
                                 (c/saturate -50)
                                 (c/lighten 33)))}]
   [:h2 {:margin [[(u/rem 3) 0 (u/rem 1) 0]]}]

   ;; application page
   ;; working around garden minifier bug that causes 1800.0px to lose the px (1800px works fine)
   ;; https://github.com/noprompt/garden/issues/120
   [:#main-content.page-application {:max-width (u/px (int (* 1.5 (:magnitude content-width))))}]
   [:#float-actions {:position :sticky
                     :top "100px"}]
   [:.reload-indicator {:position :fixed
                        :bottom "15px"
                        :right "15px"
                        :z-index 1000}] ; over re-frisk devtool

   ;; application list
   [:.rems-table
    [:.application-description
     :.application-applicant
     {:overflow :hidden
      :text-overflow :ellipsis
      :white-space :nowrap
      :max-width "30em"}

     :.application-description {:max-width "30em"}
     :.application-applicant {:max-width "10em"}]]
   [:.search-field {:display :flex
                    :flex-wrap :nowrap
                    :align-items :center}
    [:label {:margin-bottom 0}] ; override the default from Bootstrap
    [:div.input-group {:width "17em"}]]
   [:.search-tips {:font-size "0.9rem"
                   :margin "0.4rem 0"}
    [:.example-search {:background-color "#eef"
                       :padding "0.2rem"
                       :border-radius "0.25rem"}]]

   ;; !important is needed here, otherwise these attributes are overridden
   ;; by more specific styles by react-select.
   [:.dropdown-select__option--is-focused
    (make-important
     {:color (get-theme-attribute :table-heading-color "#fff")
      :background-color (get-theme-attribute :table-heading-bgcolor :color3)})]
   [:.dropdown-select__control--is-focused
    (make-important
     {:color "#495057"
      :background-color "#fff"
      :border-color "#80bdff"
      :outline "0"
      :outline-offset "-2px"
      :box-shadow "0 0 0 0.2rem rgba(0,123,255,.25)"})]

   (generate-phase-styles)
   [(s/descendant :.document :h3) {:margin-top (u/rem 4)}]

   ;; print styling
   (stylesheet/at-media
    {:print true}
    ;; workaround for firefox only printing one page of flex elements
    ;; https://github.com/twbs/bootstrap/issues/23489
    ;; https://bugzilla.mozilla.org/show_bug.cgi?id=939897
    [:.row {:display :block}]
    [:#app {:display :block}]
    [(s/> :#app :div) {:display :block}]
    [:#main-content {:display :block}]
    [:body {:display :block}]

    ;; hide some unnecessary elements
    ;; TODO: consider a hide-print class?
    [:.fixed-top {:display :none}]
    [:#actions {:display :none}]
    [:.commands {:display :none}]
    [:#member-action-forms {:display :none}]
    [:#resource-action-forms {:display :none}]
    [:.flash-message {:display :none}]

    ;; open "show more" drawers
    [".collapse:not(.show)" {:display :block}]
    [:.collapse-toggle.collapse {:display :none}])

   ;; These must be last as the parsing fails when the first non-standard element is met
   (generate-form-placeholder-styles)))

(defn- render-css-file [language content]
  (let [dir-name (str "target/resources/public/css/" (name language))
        file-name (str dir-name "/screen.css")
        dir (java.io.File. dir-name)]
    (log/info "Rendering CSS to file" (str (System/getProperty "user.dir") "/" file-name))
    (when-not (.exists dir)
      (.mkdirs dir))
    (spit file-name content)))

(defn screen-css []
  (g/css {:pretty-print? false} (remove-nil-vals (build-screen))))

(deftest test-screen-css
  (binding [context/*lang* :fi]
    (is (string? (screen-css)))))

;; For development use and Figwheel updates render all configured CSS
;; files so that Figwheel will notice this change and force our app
;; to reload CSS files from the usual route.
;; The files are not used for anything besides this signaling to Figwheel.
(mount/defstate
  rendered-css-files
  :start
  (when (env :render-css-file?)
    (doseq [language (env :languages)]
      (binding [context/*lang* language]
        (render-css-file language
                         (screen-css))))))
