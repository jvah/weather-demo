(ns weather-demo.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]

            [clojure.java.io :as io]
            [cheshire.core :as json]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]))

(def countries
  (with-open [rdr (io/reader "https://raw.githubusercontent.com/mledoze/countries/master/countries.json")]
    (doall (json/parse-stream rdr))))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(def loading-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     mount-target
     (include-js "js/app.js")]]))


(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] loading-page)

  (GET "/api/countries/" [] (response countries))
  
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (-> #'routes
                    (wrap-json-response)
                    (wrap-defaults site-defaults))]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
