(ns meuse.git
  "Interacts with a git repository"
  (:require [meuse.config :refer [config]]
            [mount.core :refer [defstate]]
            [meuse.crate :as crate]
            [meuse.metadata :as metadata]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :refer [debug info error]]
            [clojure.string :as string]))

(defprotocol Git
  (add [this])
  (git-cmd [this args])
  (commit [this msg-header msg-body])
  (pull [this])
  (push [this]))

(defrecord LocalRepository [path target lock]
  Git
  (add [this]
    (git-cmd this ["add" "."]))
  (git-cmd [this args]
    (debug "git command" (string/join " " args))
    (let [result (apply shell/sh "git" "-C" path args)]
      (debug "git command status code="(:exit result)
             "out="(:out result)
             "err="(:err result))
      (when-not (= 0 (:exit result))
        (throw (ex-info "error executing git command"
                        {:type :meuse.error/fault
                         :exit-code (:exit result)
                         :stdout (:out result)
                         :stderr (:err result)
                         :command args})))))
  (commit [this msg-header msg-body]
    (git-cmd this ["commit" "-m" msg-header "-m" msg-body]))
  (push [this]
    (git-cmd this (concat ["push"] (string/split target #"/"))))
  (pull [this]
    (git-cmd this ["pull" target])))

(defstate git
  :start (map->LocalRepository (merge (:metadata config)
                                      {:lock (java.lang.Object.)})))
