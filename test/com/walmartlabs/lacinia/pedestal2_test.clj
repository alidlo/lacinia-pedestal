; Copyright (c) 2020-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.pedestal2-test
  "Tests for the new pedestal2 namespace.

  Since the majority of the code goes through the shared internal namespace, these tests are light as long as
  the testing of the original lacinia.pedestal namespace exists."
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.lacinia.pedestal2 :as p2]
    [com.walmartlabs.lacinia.test-utils :as tu :refer [prune send-post-request]]
    [io.pedestal.http :as http]
    [cheshire.core :as cheshire]
    [clj-http.client :as client]
    [com.walmartlabs.test-reporting :refer [reporting]]))

(defn server-fixture
  [f]
  (reset! tu/*ping-subscribes 0)
  (reset! tu/*ping-cleanups 0)
  (let [service (-> (tu/compile-schema)
                    (p2/default-service nil)
                    http/create-server
                    http/start)]
    (try
      (f)
      (finally
        (http/stop service)))))

(use-fixtures :once server-fixture)
(use-fixtures :each (tu/subscriptions-fixture "ws://localhost:8888/ws"))

(deftest basic-request
  (let [response (send-post-request "{ echo(value: \"hello\") { value method }}")]
    (reporting response
               (is (= {:status 200
                       :body {:data {:echo {:method "post"
                                            :value "hello"}}}}
                      (prune response)))
               (is (= {:data {:echo {:method "post"
                                     :value "hello"}}}
                      (:body response))))))

(deftest missing-query
  (let [response (send-post-request nil)]
    (reporting response
               (is (= {:body "JSON 'query' key is missing or blank"
                       :status 400}
                      (prune response))))))

(deftest must-be-json
  (let [response (client/post "http://localhost:8888/api"
                              {:headers {"Content-Type" "text/plain"}
                               :body "does not matter"
                               :throw-exceptions false})]
    (reporting response
               (is (= {:body "Must be application/json"
                       :status 400}
                      (prune response))))))

(deftest subscriptions-ws-request
  (tu/send-init)
  (tu/expect-message {:type "connection_ack"}))
