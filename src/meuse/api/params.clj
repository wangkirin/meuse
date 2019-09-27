(ns meuse.api.params
  (:require [meuse.error :as err]
            [clojure.spec.alpha :as s]))

(defn validate-params
  "Takes a request and a spec, and validates the parameters."
  [request spec]
  (when-not (s/valid? spec request)
    (throw (ex-info (err/explain->message
                     ;; remove the db from the req to avoid json issue
                     (s/explain-data spec (dissoc request
                                                  :database
                                                  :git)))
                    {:explain-str (s/explain-str spec request)
                     :type :meuse.error/incorrect})))
  true)
