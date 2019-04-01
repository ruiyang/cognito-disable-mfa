(ns disable-mfa.core
  (:require [amazonica.aws.cognitoidp :refer :all]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def pool-id  "<pool-id>")

(defn get-user-name [u]
  (-> u
      (#(if (contains? % :attributes)
          (:attributes %)
          (:user-attributes %)))
      (#(filter (fn [a] (= (:name a) "sub")) %))
      (nth 0)
      :value
      ))

(defn get-user-email [u]
  (-> u
      (#(if (contains? % :attributes)
          (:attributes %)
          (:user-attributes %)))
      (#(filter (fn [a] (= (:name a) "email")) %))
      (nth 0)
      :value
      ))

(defn forget-user-devices [uname]
  (let [devices (->> (admin-list-devices {:user-pool-id pool-id :username  uname})
                     :devices
                     (map :device-key))]
    (for [d devices]
      (admin-forget-device {:user-pool-id pool-id :username uname :device-key d})
      )))

(defn forget-devices-and-disable-user-mfa [uname email]
  (let [devices (->> (admin-list-devices {:user-pool-id pool-id :username  uname})
                     :devices
                     (map :device-key))]
    (do
      (log/info "devices: " (count devices))
      (doall       (map #(doall
                          (log/info "Forget device " email " key: " %)
                          (admin-forget-device {:user-pool-id pool-id :username uname :device-key %})
                          )
                        devices))
      (try
        (admin-set-user-mfa-preference {:sms-mfa-settings {:Enabled false, :PreferredMfa false}
                                        :user-pool-id pool-id
                                        :username uname})
        (catch Exception e))
      (try
        (admin-set-user-mfa-preference {:software-token-mfa-settings {:Enabled false, :PreferredMfa false}
                                        :user-pool-id pool-id
                                        :username uname})
        (catch Exception e)
        ))))

(defn process-user [u]
  (let [email (get-user-email u)
        uname (get-user-name u)]
    (do
      (log/info "Processing user " email)
      (forget-devices-and-disable-user-mfa uname email)
      )))

;; (def devices (->> (admin-list-devices {:user-pool-id pool-id :username  (get-user-name u1)})
;;                   :devices
;;                   (map :device-key) 
;;                   ))

;; (let [u (admin-get-user {:user-pool-id pool-id :username "uname"})]
;;   (process-user u))

;; (get-user-name u)

;; (process-user "f673213e-6a8a-4dc6-ac7a-c0fc3f1c53ed")
;; (-> resp
;;     :users
;;     (get 1)
;;     get-user-name 
;;     process-user
;;     )


(def resp  (list-users {:user-pool-id pool-id}))
(def users (:users resp))
(def next-token (atom (:pagination-token resp)))

(for [u users]
  (process-user u))

(while (> (count @next-token) 0)
  (let [response (list-users {:user-pool-id pool-id :pagination-token @next-token})
        users (:users response)
        token (:pagination-token response)
        p (reset! next-token token)]
    (log/info token)
    (doall (map #(process-user %) users))))

;; (let [resp
;;       users (:users resp)
;;       next-token (:pagination-token resp)]
;;   (for [u users]
;;     ))
