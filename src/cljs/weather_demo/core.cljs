(ns weather-demo.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]

              [reagent-forms.core :refer [bind-fields]]
              [ajax.core :refer [GET json-response-format]]
              [clojure.string :as string]))

(def app-state (atom {}))
(def cities (reagent/cursor app-state [:cities]))
(def city-id (reagent/cursor app-state [:city :id]))

;; -------------------------
;; Views

(defn country-name [country]
  (let [name (-> country :name :common)
        region (-> country :region)]
    (string/join ", " (remove empty? [name region]))))

(defn country-selector [countries]
  [:select {:field :list :id :id}
   (for [country (sort-by country-name countries)]
     [:option {:key (:cca2 country)} (country-name country)])])

(defn country-field [countries country-atom]
  (println "Rendering country field for" (count countries) "countries")
  (if-not countries [:select [:option "Loading countries..."]]
    [bind-fields (country-selector countries) country-atom]))

(defn country-description [countries country-id]
  (println "Rendering country description for" country-id)
  (if-not country-id [:p "No country selected"]
    (let [country (first (filter #(= (:cca2 %) country-id) countries))]
      [:p "Selected country is " (country-name country)])))

(defn city-selector [cities]
  [:select {:field :list :id :city.id}
   (for [city (sort-by :name cities)]
     [:option {:key (:_id city)} (:name city)])])

(defn city-field []
  (println "Rendering city field for" (count @cities) "cities")
  (if-not @cities [:select [:option "Loading cities..."]]
    [(bind-fields (city-selector @cities) app-state)]))

(defn load-city-field [country-id]
  (when country-id
    (reset! cities nil)
    (reset! city-id nil)
    (println "Loading cities for" country-id)
    (GET (str "/api/cities/" country-id)
      {:handler #(reset! cities %)
       :response-format (json-response-format {:keywords? true})}))
  [city-field])

(defn home-page []
  [:div [:h2 "Welcome to weather-demo"]
   [:div "You might want to check out the " [:a {:href "/weather"} "weather"]]])

(defn weather-page []
  (let [countries (atom nil)
        selected-country (atom nil)]
    (GET "/api/countries/"
      {:handler #(reset! countries %)
       :response-format (json-response-format {:keywords? true})})
    (fn []
      [:div [:h2 "Weather page"]
       [country-field @countries selected-country]
       [load-city-field (:id @selected-country)]
       [country-description @countries (:id @selected-country)]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/weather" []
  (session/put! :current-page #'weather-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))
