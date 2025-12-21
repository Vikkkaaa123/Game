(ns game.core
  "Главный запускаемый файл игры.
   Запуск: lein run -m game.core"
  (:require [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

;; Флаг работы игры
(defonce game-running (atom true))

;; Обработка команд игрока
(defn handle-command [player-name input]
  (try
    (let [parts (str/split input #" ")
          command (str/lower-case (first parts))
          args (str/join " " (rest parts))]
      
      (case command
        ;; Осмотреться
        "look" (let [room (world/get-player-room player-name)]
                 (str "Вы в " (world/get-room-name room) "\n"
                      (world/get-room-desc room) "\n"
                      "Предметы: " (if-let [items (world/get-room-items room)]
                                     (if (empty? items)
                                       "нет"
                                       (str/join ", " (map items/get-item-name items)))
                                     "нет") "\n"
                      "Игроки: " (if-let [players (world/get-room-players room)]
                                   (if (empty? (disj players player-name))
                                     "никого"
                                     (str/join ", " (disj players player-name)))
                                   "никого")))
        
        ;; Взять предмет
        "взять" (if (empty? args)
                  "Укажите предмет: взять [предмет]"
                  (let [room (world/get-player-room player-name)
                        room-items (world/get-room-items room)
                        ;; Ищем предмет по частичному совпадению имени
                        item-key (first (filter #(str/includes? 
                                                  (str/lower-case (items/get-item-name %)) 
                                                  (str/lower-case args))
                                                room-items))]
                    (if item-key
                      (do
                        (world/add-to-inventory! player-name item-key)
                        (world/remove-item-from-room! room item-key)
                        (str "Вы взяли: " (items/get-item-name item-key)))
                      "Такого предмета здесь нет")))
        
        ;; Положить предмет
        "положить" (if (empty? args)
                     "Укажите предмет: положить [предмет]"
                     (let [inventory (world/get-player-inventory player-name)
                           ;; Ищем предмет в инвентаре
                           item-key (first (filter #(str/includes?
                                                     (str/lower-case (items/get-item-name %)) 
                                                     (str/lower-case args))
                                                   inventory))]
                       (if item-key
                         (do
                           (world/remove-from-inventory! player-name item-key)
                           (let [room (world/get-player-room player-name)]
                             (world/add-item-to-room! room item-key))
                           (str "Вы положили: " (items/get-item-name item-key)))
                         "У вас нет такого предмета")))
        
        ;; Показать инвентарь
        "инвентарь" (let [inv (world/get-player-inventory player-name)]
                      (if (empty? inv)
                        "Ваш инвентарь пуст"
                        (str "Ваш инвентарь:\n" 
                             (str/join "\n" (map items/get-item-name inv)))))
        
        ;; Перемещение
        "идти" (if (empty? args)
                 "Укажите направление: идти [север/юг/запад/восток]"
                 (let [direction (keyword args)
                       current-room (world/get-player-room player-name)
                       target-room (world/get-exit-room current-room direction)]
                   (if target-room
                     (do
                       (world/set-player-room! player-name target-room)
                       (handle-command player-name "look"))
                     "Нельзя пойти в этом направлении")))
        
        ;; Короткие команды движения
        "с" (handle-command player-name "идти север")
        "ю" (handle-command player-name "идти юг")
        "з" (handle-command player-name "идти запад")
        "в" (handle-command player-name "идти восток")
        
        ;; Сказать в комнате
        "сказать" (if (empty? args)
                    "Скажите что-нибудь: сказать [текст]"
                    (let [room (world/get-player-room player-name)
                          other-players (world/get-players-in-room room player-name)]
                      (str "Вы сказали: \"" args "\"\n"
                           (if (empty? other-players)
                             "Но в комнате никого нет"
                             (str "Вас слышат: " (str/join ", " other-players))))))
        
        ;; Помощь
        "помощь" (str "Доступные команды:\n"
                      "look - осмотреться\n"
                      "взять [предмет] - взять предмет\n"
                      "положить [предмет] - положить предмет\n"
                      "инвентарь - показать инвентарь\n"
                      "идти [север/юг/запад/восток] - переместиться\n"
                      "с/ю/з/в - короткие команды движения\n"
                      "сказать [текст] - сказать в комнате\n"
                      "помощь - эта справка\n"
                      "выход - выйти из игры")
        
        ;; Выход из игры
        "выход" (do
                  (world/remove-player! player-name)
                  (reset! game-running false)
                  "До свидания!")
        
        ;; Неизвестная команда
        "Неизвестная команда. Введите 'помощь' для списка команд."))
    
    (catch Exception e
      (str "Ошибка выполнения команды: " (.getMessage e)))))

;; Главная функция игры
(defn -main
  "Точка входа в игру"
  [& args]
  (println "ИГРА 'ПОБЕГ ИЗ ЛАБОРАТОРИИ'")
  (println "Кооперативная текстовая игра на Clojure с STM")
  (println "Создано командой разработчиков")
  
  (println "\nДобро пожаловать!")
  (print "Введите ваше имя: ")
  (flush)
  
  (let [player-name (read-line)]
    (when (and player-name (not (str/blank? player-name)))
      ;; Добавляем игрока в мир
      (world/add-player! player-name)
      
      (println (str "\nЗдравствуйте, " player-name "!"))
      (println "Вы находитесь в заброшенной лаборатории.")
      (println "Введите 'помощь' для списка команд.")
      (println (handle-command player-name "look"))
      
      ;; Главный игровой цикл
      (while @game-running
        (print "\n> ")
        (flush)
        
        (let [input (read-line)]
          (when (and input (not (str/blank? input)))
            (let [result (handle-command player-name input)]
              (println result))))))
    
    (println "\nИгра завершена. Спасибо за игру!")))

;; Сообщение при загрузке модуля
(println "[CORE] Основной модуль игры загружен")

;; Для работы в REPL (без запуска сервера)
(comment
  ;; Ручное тестирование в REPL:
  
  ;; 1. Добавить игрока
  (world/add-player! "Тест")
  
  ;; 2. Посмотреть комнату
  (handle-command "Тест" "look")
  
  ;; 3. Взять предмет (если есть)
  (handle-command "Тест" "взять ключ")
  
  ;; 4. Посмотреть инвентарь
  (handle-command "Тест" "инвентарь")
  
  ;; 5. Переместиться
  (handle-command "Тест" "идти север")
  
  ;; 6. Выйти
  (handle-command "Тест" "выход")
)
