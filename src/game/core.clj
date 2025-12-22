(ns game.core
  "Основная логика игры. Без консольного ввода/вывода."
  (:require [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

;; Статический флаг (для многопользовательской игры нужно атом)
(def ^:private game-running true)

;; Главная функция обработки команд - ТОЛЬКО ЛОГИКА
(defn handle-command [player-name input]
  (try
    (let [parts (str/split (str/trim input) #"\s+")
          command (when (seq parts) (str/lower-case (first parts)))
          args (str/join " " (rest parts))]
      
      (cond
        ;; 1. ОСМОТРЕТЬСЯ
        (and command (contains? #{"look" "осмотреть" "посмотреть" "осмотреться" "l"} command))
        (let [room (world/get-player-room player-name)]
          (str "Вы в " (world/get-room-name room) "\n"
               (world/get-room-desc room) "\n"
               "Предметы: " (if-let [room-items (world/get-room-items room)]
                              (if (seq room-items)
                                (->> room-items
                                     (keep items/get-item-name)
                                     (str/join ", "))
                                "нет")
                              "нет") "\n"
               "Игроки: " (if-let [players (world/get-room-players room)]
                            (let [other-players (disj players player-name)]
                              (if (seq other-players)
                                (str/join ", " other-players)
                                "никого"))
                            "никого")))
        
        ;; 2. ВЗЯТЬ ПРЕДМЕТ
        (and command (contains? #{"взять" "take" "поднять" "t"} command))
        (if (str/blank? args)
          "Укажите предмет: взять [предмет]"
          (let [room (world/get-player-room player-name)
                room-items (world/get-room-items room)
                ;; Ищем предмет по частичному совпадению
                item-key (first (filter #(let [item-name (items/get-item-name %)]
                                           (and item-name 
                                                (str/includes? 
                                                 (str/lower-case item-name) 
                                                 (str/lower-case args))))
                                        room-items))]
            (if item-key
              (do
                (world/add-to-inventory! player-name item-key)
                (world/remove-item-from-room! room item-key)
                (str "Вы взяли: " (items/get-item-name item-key)))
              "Такого предмета здесь нет")))
        
        ;; 3. ПОЛОЖИТЬ ПРЕДМЕТ
        (and command (contains? #{"положить" "drop" "бросить" "пол"} command))
        (if (str/blank? args)
          "Укажите предмет: положить [предмет]"
          (let [inventory (world/get-player-inventory player-name)
                item-key (first (filter #(let [item-name (items/get-item-name %)]
                                           (and item-name
                                                (str/includes?
                                                 (str/lower-case item-name)
                                                 (str/lower-case args))))
                                        inventory))]
            (if item-key
              (do
                (world/remove-from-inventory! player-name item-key)
                (let [room (world/get-player-room player-name)]
                  (world/add-item-to-room! room item-key))
                (str "Вы положили: " (items/get-item-name item-key)))
              "У вас нет такого предмета")))
        
        ;; 4. ИНВЕНТАРЬ
        (and command (contains? #{"инвентарь" "inventory" "инв" "i"} command))
        (let [inventory (world/get-player-inventory player-name)]
          (if (seq inventory)
            (str "Ваш инвентарь:\n" 
                 (->> inventory
                      (keep items/get-item-name)
                      (str/join "\n")))
            "Ваш инвентарь пуст"))
        
        ;; 5. ПЕРЕМЕЩЕНИЕ
        (and command (contains? #{"идти" "go" "g"} command))
        (if (str/blank? args)
          "Укажите направление: идти [север/юг/запад/восток]"
          (let [direction-map {"север" :north "юг" :south "запад" :west "восток" :east
                               "north" :north "south" :south "west" :west "east" :east}
                direction (get direction-map (str/lower-case args))
                current-room (world/get-player-room player-name)
                target-room (when direction (world/get-exit-room current-room direction))]
            (if target-room
              (do
                (world/set-player-room! player-name target-room)
                (handle-command player-name "look"))
              "Нельзя пойти в этом направлении")))
        
        ;; 6. КОРОТКИЕ КОМАНДЫ ДВИЖЕНИЯ
        (and command (contains? #{"с" "север" "north" "n"} command))
        (handle-command player-name "идти север")
        
        (and command (contains? #{"ю" "юг" "south" "s"} command))
        (handle-command player-name "идти юг")
        
        (and command (contains? #{"з" "запад" "west" "w"} command))
        (handle-command player-name "идти запад")
        
        (and command (contains? #{"в" "восток" "east" "e"} command))
        (handle-command player-name "идти восток")
        
        ;; 7. СКАЗАТЬ В КОМНАТЕ
        (and command (contains? #{"сказать" "say" "с"} command))
            (if (str/blank? args)
              "Скажите что-нибудь: сказать [текст]"
              ;; Вместо возврата строки, возвращаем структуру для API
              {:type :chat-message
              :from player-name
              :message args
              :action :broadcast})
        
        ;; 8. ПОМОЩЬ
        (and command (contains? #{"помощь" "help" "справка" "h" "?"} command))
        (str "Доступные команды:\n"
             "look / осмотреть - осмотреться\n"
             "взять [предмет] / take - взять предмет\n"
             "положить [предмет] / drop - положить предмет\n"
             "инвентарь / inventory - показать инвентарь\n"
             "идти [направление] / go - переместиться\n"
             "с/ю/з/в / n/s/w/e - короткие команды движения\n"
             "сказать [текст] / say - сказать в комнате\n"
             "помощь / help - эта справка\n"
             "выход / exit - выйти из игры")
        
        ;; 9. ВЫХОД
        (and command (contains? #{"выход" "exit" "quit" "выйти" "q"} command))
        (do
          (world/remove-player! player-name)
          "Вы вышли из игры")
        
        ;; 10. НЕИЗВЕСТНАЯ КОМАНДА
        :else
        "Неизвестная команда. Введите 'помощь' для списка команд."))
    
    (catch Exception e
      (str "Ошибка выполнения команды: " (.getMessage e)))))

;; Функция для запуска из консоли (опционально)
(defn -main [& args]
  (println "🎮 Для веб-интерфейса запустите: lein run -m game.server.websocket")
  (println "📡 Web-сервер: http://localhost:8080"))

;; Сообщение при загрузке
(println "[CORE] Основная логика игры загружена")