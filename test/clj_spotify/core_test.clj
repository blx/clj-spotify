(ns clj-spotify.core-test
  (:require [clojure.test :refer :all]
            [clj-spotify.core :as sptfy]
            [clojure.data.json :as json]
            [clojure.data :as data]
            [clj-http.client :as client]
            [clojure.data.codec.base64 :as b64]
            ))

(def album-data-file "./test/clj_spotify/test-data/album.json")
(def albums-data-file "./test/clj_spotify/test-data/albums.json")
(def track-of-album-data-file "./test/clj_spotify/test-data/tracks-of-album.json")
(def artist-data-file "./test/clj_spotify/test-data/artist.json")
(def artists-data-file "./test/clj_spotify/test-data/artists.json")
(def artists-albums-file "./test/clj_spotify/test-data/artists-albums.json")
(def artists-top-tracks-file "./test/clj_spotify/test-data/artists-top-tracks.json")
(def artists-related-artists-file "./test/clj_spotify/test-data/artists-related-artists.json")
(def featured-playlists-file "./test/clj_spotify/test-data/featured-playlists.json")
(def browse-categories-file "./test/clj_spotify/test-data/list-of-browse-categories.json")
(def category-file "./test/clj_spotify/test-data/category.json")
(def categorys-playlists-file "./test/clj_spotify/test-data/categorys-playlists.json")
(def playlist-file "./test/clj_spotify/test-data/playlist.json")
(def playlists-tracks-file "./test/clj_spotify/test-data/playlists-tracks.json")
(def user-profile-file "./test/clj_spotify/test-data/user-profile.json")
(def search-file "./test/clj_spotify/test-data/search.json")
(def get-a-track-file "./test/clj_spotify/test-data/get-a-track.json")
(def get-several-tracks-file "./test/clj_spotify/test-data/get-several-tracks.json")
 
(def enc-auth-string
  (str "Basic "
  (->
    (str (System/getenv "SPOTIFY_CLIENT_ID") ":" (System/getenv "SPOTIFY_SECRET_TOKEN"))
    (.getBytes)
    (b64/encode)
    (String. "UTF-8"))))

(def spotify-oauth-token (->
                           "https://accounts.spotify.com/api/token" 
                           (client/post {:form-params {:grant_type "client_credentials"} :headers {:Authorization enc-auth-string}})
                           :body 
                           (json/read-str :key-fn keyword)
                           :access_token))

(defn reset-volatile-vals
  "Function to reset values that change over time such as amount of followers or popularity ranking."
  [k v]
  (condp
    :followers {:href nil, :total 0}
    :popularity 0
    :total 0
    :snapshot_id "123456"
    (and (string? v) (.contains v "scdn.co")) "https://scdn.co/preview/ref"
    :else v))

(defn test-json-string-to-map [s]
  "Read string and transform to json but ignore key :followers"
  (json/read-str s :value-fn reset-volatile-vals :key-fn keyword))

(def correct-map {:test-key "test-value" :test-map {:a "a"} :test-vector [1 2 3] :test-null nil})

(def correctly-formatted-response {:body "{\"test-key\": \"test-value\", \"test-map\" : {\"a\": \"a\"}, \"test-vector\" : [1 2 3], \"test-null\" : null}"}) 

(def missing-body-tag-response {}) 

(def malformed-json-response {:body "{\"test-key\": \"test-value\", \"test-map\" : {\"a\": \"a\"}, \"test-vector\" : [1 2 3], \"test-null\" : }"}) 

(def nullpointer-error-map {:error {:status "NullPointerException", :message nil}}) 

(def json-missing-key-error-map {:error {:status "Exception", :message "JSON error (key missing value in object)"}})

(def test-url (str sptfy/spotify-api-url "users/user_id/playlists/playlist_id/tracks"))
(def correct-test-url (str sptfy/spotify-api-url "users/elkalel/playlists/6IIjEBw2BrRXbrSLerA7A6/tracks"))

(def correct-param-map {:user_id "elkalel" :playlist_id "6IIjEBw2BrRXbrSLerA7A6"})
(def keys-not-present-param-map {:category_id "pop" :owner_id "elkalel"})

(deftest test-response-to-map
  (testing "Conversion from string to clojure map"
    (is (= correct-map (sptfy/response-to-map correctly-formatted-response))))
  (testing "Missing body tag in response."
    (is (= nullpointer-error-map (sptfy/response-to-map missing-body-tag-response)))) 
  (testing "Malformed json syntax in string."
    (is (= json-missing-key-error-map (sptfy/response-to-map malformed-json-response)))))

(deftest test-replace-url-values
  (testing "Replace template values in spotify url and compare to correct url."
    (is (= correct-test-url (sptfy/replace-url-values correct-param-map test-url))))
  (testing "Call replace-url-values with empty map and empty url"
    (is (= "" (sptfy/replace-url-values {} ""))))
  (testing "Call replace-url-values with key in param-map not present in url"
    (is (= test-url (sptfy/replace-url-values keys-not-present-param-map test-url)))))

(deftest test-get-an-album
  (testing "Get a spotify album and verify the json data to be equal to test data in album.json"
    ( with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp album-data-file))
            differences (data/diff (sptfy/get-an-album {:id "0sNOF9WDwhWunNAHPD3Baj"} spotify-oauth-token) correct-test-data)]  
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-several-albums
  (testing "Get several spotify albums and verify the json data to be equal to test data in albums.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp albums-data-file))
            differences (data/diff (sptfy/get-several-albums {:ids ["41MnTivkwTO3UUJ8DrqEJJ" ,"6JWc4iAiJ9FjyK0B59ABb4","6UXCm6bOO4gFlDQZV5yL37"]} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-tracks-of-an-album
  (testing "Get the tracks of a spotify album and verify the json data to be equal to test data in tracks-of-an-album.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp track-of-album-data-file))
            differences (data/diff (sptfy/get-an-albums-tracks {:id "6akEvsycLGftJxYudPjmqK"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-an-artist
  (testing "Get an artist and verify the json data to be equal to test data in artist.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp artist-data-file))
            differences (data/diff  (sptfy/get-an-artist {:id "0OdUWJ0sBjDrqHygGUXeCF"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-several-artists
  (testing "Get several artists and verify the json data to be equal to test data in artist.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp artists-data-file))
            differences (data/diff ( sptfy/get-several-artists {:ids ["0oSGxfWSnnOXhD2fKuz2Gy","3dBVyJ7JuOMt4GE9607Qin"]} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-an-artists-albums
  (testing "Get an artists albums and verify the json data to be equal to test data in artist.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp artists-albums-file))
            differences (data/diff (sptfy/get-an-artists-albums {:id "1H1jG5SxsBzeuUk3ktdbeG"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))


(deftest test-get-an-artists-top-tracks
  (testing "Get spotify information about an artists top tracks, due to ever changing data, only verify a status 200 response."
    (is  (= 200 (:status (meta (sptfy/get-an-artists-top-tracks {:id "0TnOYISbd1XYRBk9myaseg" :country "ES" } spotify-oauth-token)))))))

(deftest test-get-an-artists-related-artists
  (testing "Get spotify information about similar artists, due to ever changing data, only verify a status 200 response."
    (is (= 200 (:status (meta (sptfy/get-an-artists-related-artists {:id "5ZKMPRDHc7qElVJFh3uRqB"} spotify-oauth-token)))))))


(deftest test-get-a-list-of-featured-playlists
  (testing "Get a list of spotify featured playlists, due to the data changing according to the time of day, only verify status 200 in response."
    (is (= 200 (:status (meta (sptfy/get-a-list-of-featured-playlists {:country "US" :timestamp "2014-10-23T07:00:00"}  spotify-oauth-token)))))))

(deftest test-get-a-list-of-new-releases
  (testing "Get a list of new releases, due to ever changing data, only verify a status 200 response."
    (is (= 200 (:status (meta (sptfy/get-a-list-of-new-releases {:country "SE" :limit 2} spotify-oauth-token)))))))

(deftest test-get-a-list-of-browse-categories
  (testing "Get a list of spotify browse categories and verify the json data to be equal to test data in list-of-browse-categories.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp browse-categories-file))
            differences (data/diff ( sptfy/get-a-list-of-categories {:locale "sv_SE" :country "SE" :limit 10 :offset 5}  spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-a-category
  (testing "Get a spotify category and verify the json data to be equal to test data in category.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp category-file))
            differences (data/diff ( sptfy/get-a-category {:category_id "dinner" :locale "sv_SE" :country "SE"}  spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-a-categorys-playlists
  (testing "Get a spotify category's playlist, due to ever changing data, only verify a status 200 response."
    (= 200 (:status (meta (sptfy/get-a-categorys-playlists {:category_id "dinner" :country "SE" :limit 10 :offset 5} spotify-oauth-token))))))

(deftest test-get-a-playlist
  (testing "Get a spotify playlist and verify the json data to be equal to test data in user-playlist.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp playlist-file))
            differences (data/diff (sptfy/get-a-playlist {:owner_id "elkalel" :playlist_id "5X6O7Wb8y3we9VzYTT9l64" :market "SE"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-a-playlists-tracks
  (testing "Get a spotify playlist's tracks and verify the json data to be equal to test data in playlists-tracks.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp playlists-tracks-file))
            differences (data/diff (sptfy/get-a-playlists-tracks {:owner_id "elkalel" :playlist_id "5X6O7Wb8y3we9VzYTT9l64" :offset 0 :limit 100 :market "SE"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))


(deftest test-get-a-users-profile
  (testing "Get a users profile and verify the json data to be equal to test data in user-profile.json"
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp user-profile-file))
            differences (data/diff (sptfy/get-a-users-profile {:user_id "elkalel"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-search
  (testing "Search spotify."
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp search-file))
            differences (data/diff (sptfy/search {:q "Muse" :type "track,artist" :market "US" :limit 1 :offset 5} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-a-track
  (testing "Get information about a single track."
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp get-a-track-file))
            differences (data/diff (sptfy/get-a-track {:id "1zHlj4dQ8ZAtrayhuDDmkY" :market "ES"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))

(deftest test-get-several-tracks
  (testing "Get information about several tracks."
    (with-redefs [sptfy/json-string-to-map test-json-string-to-map]
      (let [correct-test-data (test-json-string-to-map (slurp get-several-tracks-file))
            differences (data/diff (sptfy/get-several-tracks {:ids "7ouMYWpwJ422jRcDASZB7P,4VqPOruhp5EdPBeR92t6lQ,2takcwOaAZWiXQijPHIx7B" :market "ES"} spotify-oauth-token) correct-test-data)]
        (is (= nil (first differences) (second differences)))))))




