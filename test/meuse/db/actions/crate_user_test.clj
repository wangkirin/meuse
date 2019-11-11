(ns meuse.db.actions.crate-user-test
  (:require [meuse.auth.password :as password]
            [meuse.db :refer [database]]
            [meuse.db.actions.user :as user-db]
            [meuse.db.actions.crate-user :as crate-user-db]
            [meuse.db.actions.crate :as crate-db]
            [meuse.db.actions.role :as role-db]
            [meuse.db.actions.token :as token-db]
            [meuse.helpers.fixtures :refer :all]
            [clojure.test :refer :all]
            [meuse.db.actions.role :as role])
  (:import clojure.lang.ExceptionInfo))

(use-fixtures :once system-fixture)
(use-fixtures :each db-clean-fixture table-fixture)

(deftest create-test
  (let [user {:name "mathieu"
              :password "foobar"
              :description "it's me mathieu"
              :active true
              :role "admin"}]
    (testing "success"
      (user-db/create database user)
      (crate-user-db/create database "crate1" (:name user))
      (let [crate-db-id (:crate-id (crate-db/by-name
                                    database
                                    "crate1"))
            user-db-id (:user-id (user-db/by-name database (:name user)))
            crate-user (crate-user-db/by-id
                        database
                        crate-db-id
                        user-db-id)]
        (is (= {:crate-id crate-db-id
                :user-id user-db-id}
               crate-user))))
    (testing "error"
      (is (thrown-with-msg? ExceptionInfo
                            #"does not exist$"
                            (crate-user-db/create database "oups" (:name user))))
      (is (thrown-with-msg? ExceptionInfo
                            #"does not exist$"
                            (crate-user-db/create database "crate1" "oups")))
      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (format "already owns the crate %s$"
                                                "crate1"))
                            (crate-user-db/create database "crate1" (:name user)))))))

(deftest delete-test
  (testing "success"
    (crate-user-db/delete database "crate1" "user2")
    (let [crate-db-id (:crate-id (crate-db/by-name
                                  database
                                  "crate1"))
          user-db-id (:user-id (user-db/by-name database "user2"))]
      (is (nil? (crate-user-db/by-id database crate-db-id user-db-id)))))
  (testing "error"
    (is (thrown-with-msg? ExceptionInfo
                          #"the crate oups does not exist$"
                          (crate-user-db/delete database "oups" "user2")))
    (is (thrown-with-msg? ExceptionInfo
                          #"the user oups does not exist$"
                          (crate-user-db/delete database "crate1" "oups")))
    (is (thrown-with-msg? ExceptionInfo
                          #"the user user2 does not own the crate crate1"
                          (crate-user-db/delete database "crate1" "user2")))))

(deftest create-crate-users-test
  (testing "success"
    (crate-user-db/create-crate-users database
                                      "crate2"
                                      ["user2" "user3"])
    (let [crate-db-id (:crate-id (crate-db/by-name
                                  database
                                  "crate2"))
          ;; user1 is created by the test fixture
          user0-db-id (:user-id (user-db/by-name database "user1"))
          user1-db-id (:user-id (user-db/by-name database "user2"))
          user2-db-id (:user-id (user-db/by-name database "user3"))
          crate-user1 (crate-user-db/by-id
                       database
                       crate-db-id
                       user1-db-id)
          crate-user2 (crate-user-db/by-id
                       database
                       crate-db-id
                       user2-db-id)]
      (is (= {:crate-id crate-db-id
              :user-id user1-db-id}
             crate-user1))
      (is (= {:crate-id crate-db-id
              :user-id user2-db-id}
             crate-user2))
      (let [crate-users (user-db/crate-owners database "crate2")]
        (is (int? (:user-cargo-id (first crate-users))))
        (is (int? (:user-cargo-id (second crate-users))))
        (is (= (set [{:user-id user0-db-id
                      :user-name "user1"
                      :crate-id crate-db-id}
                     {:user-id user1-db-id
                      :user-name "user2"
                      :crate-id crate-db-id}
                     {:user-id user2-db-id
                      :user-name "user3"
                      :crate-id crate-db-id}])
               (set (map #(dissoc % :user-cargo-id) crate-users))))))))

(deftest delete-crate-users-test
  (testing "success"
    (crate-user-db/delete-crate-users database
                                      "crate1"
                                      ["user2" "user3"])
    (let [crate-db-id (:crate-id (crate-db/by-name
                                  database
                                  "crate1"))
          user1-db-id (:user-id (user-db/by-name database "user2"))
          user2-db-id (:user-id (user-db/by-name database "user3"))]
      (is (nil? (crate-user-db/by-id database crate-db-id user1-db-id)))
      (is (nil? (crate-user-db/by-id database crate-db-id user2-db-id))))))

(deftest owned-by?-test
  (testing "success"
    (let [user2-id (:user-id (user-db/by-name database "user2"))]
      (is (crate-user-db/owned-by? database "crate1" user2-id))))
  (testing "failures"
    (let [user4-id (:user-id (user-db/by-name database "user4"))]
      (is (thrown-with-msg? ExceptionInfo
                            #"user does not own the crate"
                            (crate-user-db/owned-by? database "crate1" user4-id)))
      (is (thrown-with-msg? ExceptionInfo
                            #"the crate doesnotexist does not exist"
                            (crate-user-db/owned-by? database "doesnotexist" user4-id))))))
