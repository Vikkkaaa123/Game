(ns game.game.world
  "Ядро игрового мира - STM система.
   Главное состояние игры: rooms, players, items, puzzles.
   Все изменения через транзакции dosync.")

;; ГЛАВНОЕ СОСТОЯНИЕ МИРА

(defonce world-state
  (ref {

        
    ;; КОМНАТЫ (основная структура мира)

    :rooms {
      :start {
        :name "Стартовая комната"
        :desc "Вы находитесь в заброшенной лаборатории. Вокруг разбросано оборудование. Стены исписаны формулами. На столе микроскоп и разобранный прибор."
        :exits {:north :hallway}
        :items #{"ключ-карта" "микроскоп" "провод"}
        :puzzle nil
        :players #{}
        :locked-exits #{}
      }
      
      :hallway {
        :name "Коридор"
        :desc "Длинный коридор с дверьми по обеим сторонам. На стене висит схема лаборатории. Одна дверь ведет в архив, другая - в серверную."
        :exits {:south :start :east :promenade :west :archive}
        :items #{"схема"}
        :puzzle {
          :type :door-code
          :solved false
          :hint "Код от серверной: 3XX7 (X = номер месяца основания лаборатории)"
          :solution "3107"
          :attempts 0
        }
        :players #{}
        :locked-exits #{:west}  ;; Дверь в архив закрыта
      }
      
      :promenade {
        :name "Променад"
        :desc "Просторное помещение с панорамным окном. Вид на закрытый двор лаборатории. В центре стоит неработающая консоль управления."
        :exits {:west :hallway}
        :items #{"консоль"}
        :puzzle {
          :type :repair-console
          :solved false
          :required-items #{"провод" "схема"}
          :message "Консоль требует починки. Нужны провод и схема подключения."
        }
        :players #{}
        :locked-exits #{}
      }
      
      :archive {
        :name "Архив"
        :desc "Комната с архивными стеллажами. Пахнет старыми книгами и пылью. На столе лежат журналы и документы."
        :exits {:east :hallway}
        :items #{"лабораторный-журнал" "формулы"}
        :puzzle {
          :type :find-date
          :solved false
          :question "В каком месяце была основана лаборатория? (номер месяца)"
          :answer "10"
          :found false
        }
        :players #{}
        :locked-exits #{}  ;; Изначально открыта, но доступ через hallway заблокирован
      }
    }
    

    ;; ИГРОКИ (динамически заполняется при подключении)

    :players {}  ;; Структура: {"имя" {:room :start :inventory #{} ...}}
    

    ;; ГОЛОВОЛОМКИ (глобальное состояние)

        
    :puzzles {
      :main-door {:solved false :required [:lab-solved :console-solved]}
      :lab-solved false
      :console-solved false
      :archive-accessed false
    }


        
    ;; ПРЕДМЕТЫ (глобальный реестр)

    :items-registry {
      "ключ-карта" {
        :name "Ключ-карта"
        :desc "Пластиковая карта с магнитной полосой. Имеет логотип лаборатории."
        :type :key
        :usable-in [:hallway]
        :effect "Открывает дверь в архив при правильном коде"
      }
      "микроскоп" {
        :name "Микроскоп"
        :desc "Электронный микроскоп. Под линзой слайд с цифрами: '3XX7'"
        :type :tool
        :examinable true
        :hint "На слайде написано: 'X = номер месяца основания лаборатории'"
      }
      "провод" {
        :name "Медный провод"
        :desc "Длинный медный провод с изоляцией. Кажется, его можно использовать для починки."
        :type :component
        :usable-in [:promenade]
      }
      "схема" {
        :name "Схема лаборатории"
        :desc "Подробная схема всей лаборатории. Отмечены все комнаты и соединения."
        :type :document
        :usable-in [:promenade]
        :info "На схеме отмечено: 'Основана: октябрь 1997'"
      }
      "консоль" {
        :name "Консоль управления"
        :desc "Центральная консоль управления лабораторией. Не работает без провода и схемы."
        :type :device
        :fixed false
      }
      "лабораторный-журнал" {
        :name "Лабораторный журнал"
        :desc "Старый журнал записей. На первой странице: 'Лаборатория основана в октябре 1997 года.'"
        :type :document
        :examinable true
      }
      "формулы" {
        :name "Научные формулы"
        :desc "Лист с математическими формулами. В углу мелко: 'Код для ежедневного доступа: 3107'"
        :type :document
        :examinable true
      }
    }

        
    ;; ИГРОВЫЕ СОБЫТИЯ (лог)

    :events []
    

    ;; ВРЕМЯ ИГРЫ

    :start-time (System/currentTimeMillis)
    :game-started true
  }))




;; ОСНОВНЫЕ ФУНКЦИИ ДЛЯ РАБОТЫ С МИРОМ


(defn get-room
  "Получить информацию о комнате (без транзакции)"
  [room-name]
  (get-in @world-state [:rooms room-name]))

(defn get-room-name
  "Получить имя комнаты"
  [room-name]
  (get-in @world-state [:rooms room-name :name]))

(defn get-room-desc
  "Получить описание комнаты"
  [room-name]
  (get-in @world-state [:rooms room-name :desc]))

(defn get-room-exits
  "Получить выходы из комнаты"
  [room-name]
  (get-in @world-state [:rooms room-name :exits]))

(defn get-room-items
  "Получить предметы в комнате"
  [room-name]
  (get-in @world-state [:rooms room-name :items]))

(defn get-room-players
  "Получить игроков в комнате"
  [room-name]
  (get-in @world-state [:rooms room-name :players]))

(defn get-room-puzzle
  "Получить головоломку в комнате"
  [room-name]
  (get-in @world-state [:rooms room-name :puzzle]))



;; ФУНКЦИИ ОБНОВЛЕНИЯ 

(defn update-room!
  "Обновить комнату (транзакция)"
  [room-name f & args]
  (dosync
    (apply alter world-state update-in [:rooms room-name] f args)))

(defn add-item-to-room!
  "Добавить предмет в комнату"
  [room-name item]
  (dosync
    (alter world-state update-in [:rooms room-name :items] conj item)))

(defn remove-item-from-room!
  "Удалить предмет из комнаты"
  [room-name item]
  (dosync
    (alter world-state update-in [:rooms room-name :items] disj item)))

(defn move-item-between-rooms!
  "Переместить предмет между комнатами"
  [from-room to-room item]
  (dosync
    (alter world-state update-in [:rooms from-room :items] disj item)
    (alter world-state update-in [:rooms to-room :items] conj item)))

(defn add-player-to-room!
  "Добавить игрока в комнату"
  [room-name player-name]
  (dosync
    (alter world-state update-in [:rooms room-name :players] conj player-name)))

(defn remove-player-from-room!
  "Удалить игрока из комнаты"
  [room-name player-name]
  (dosync
    (alter world-state update-in [:rooms room-name :players] disj player-name)))

(defn move-player-between-rooms!
  "Переместить игрока между комнатами"
  [from-room to-room player-name]
  (dosync
    (alter world-state update-in [:rooms from-room :players] disj player-name)
    (alter world-state update-in [:rooms to-room :players] conj player-name)))

(defn set-puzzle-solved!
  "Пометить головоломку как решенную"
  [room-name]
  (dosync
    (alter world-state assoc-in [:rooms room-name :puzzle :solved] true)))

(defn unlock-exit!
  "Разблокировать выход из комнаты"
  [room-name exit]
  (dosync
    (alter world-state update-in [:rooms room-name :locked-exits] disj exit)))

(defn lock-exit!
  "Заблокировать выход из комнаты"
  [room-name exit]
  (dosync
    (alter world-state update-in [:rooms room-name :locked-exits] conj exit)))

(defn exit-locked?
  "Проверить, заблокирован ли выход"
  [room-name exit]
  (contains? (get-in @world-state [:rooms room-name :locked-exits]) exit))

(defn get-exit-room
  "Получить комнату по выходу (с проверкой блокировки)"
  [from-room direction]
  (let [exits (get-room-exits from-room)
        target-room (get exits direction)]
    (when (and target-room (not (exit-locked? from-room direction)))
      target-room)))




;; ФУНКЦИИ ДЛЯ РАБОТЫ С ПРЕДМЕТАМИ


(defn get-item-info
  "Получить информацию о предмете из реестра"
  [item-name]
  (get-in @world-state [:items-registry item-name]))

(defn item-exists?
  "Проверить существование предмета в реестре"
  [item-name]
  (contains? (get-in @world-state [:items-registry]) item-name))

(defn item-in-room?
  "Проверить, есть ли предмет в комнате"
  [room-name item-name]
  (contains? (get-room-items room-name) item-name))

(defn item-usable-in?
  "Проверить, можно ли использовать предмет в данной комнате"
  [item-name room-name]
  (let [item-info (get-item-info item-name)]
    (when item-info
      (contains? (set (:usable-in item-info)) room-name))))



;; ФУНКЦИИ ДЛЯ ИГРОКОВ (базовые)

(defn add-player!
  "Добавить нового игрока в мир"
  [player-name]
  (dosync
    (alter world-state assoc-in [:players player-name] 
           {:room :start
            :inventory #{}
            :joined-at (System/currentTimeMillis)
            :actions-count 0})
    ;; Добавляем игрока в стартовую комнату
    (alter world-state update-in [:rooms :start :players] conj player-name)))

(defn remove-player!
  "Удалить игрока из мира"
  [player-name]
  (dosync
    (let [room (get-in @world-state [:players player-name :room])]
      ;; Удаляем из комнаты
      (when room
        (alter world-state update-in [:rooms room :players] disj player-name))
      ;; Удаляем из списка игроков
      (alter world-state update-in [:players] dissoc player-name))))

(defn get-player
  "Получить информацию об игроке"
  [player-name]
  (get-in @world-state [:players player-name]))

(defn get-player-room
  "Получить комнату игрока"
  [player-name]
  (get-in @world-state [:players player-name :room]))

(defn set-player-room!
  "Установить комнату игрока"
  [player-name room-name]
  (dosync
    (alter world-state assoc-in [:players player-name :room] room-name)))

(defn get-player-inventory
  "Получить инвентарь игрока"
  [player-name]
  (get-in @world-state [:players player-name :inventory]))

(defn add-to-inventory!
  "Добавить предмет в инвентарь игрока"
  [player-name item]
  (dosync
    (alter world-state update-in [:players player-name :inventory] conj item)))

(defn remove-from-inventory!
  "Удалить предмет из инвентаря игрока"
  [player-name item]
  (dosync
    (alter world-state update-in [:players player-name :inventory] disj item)))

(defn player-has-item?
  "Проверить, есть ли у игрока предмет"
  [player-name item]
  (contains? (get-player-inventory player-name) item))




;; ФУНКЦИИ ДЛЯ ГОЛОВОЛОМОК

(defn puzzle-solved?
  "Проверить, решена ли головоломка в комнате"
  [room-name]
  (get-in @world-state [:rooms room-name :puzzle :solved]))

(defn get-puzzle-type
  "Получить тип головоломки"
  [room-name]
  (get-in @world-state [:rooms room-name :puzzle :type]))

(defn get-puzzle-hint
  "Получить подсказку головоломки"
  [room-name]
  (get-in @world-state [:rooms room-name :puzzle :hint]))

(defn set-global-puzzle-solved!
  "Установить глобальную головоломку как решенную"
  [puzzle-key]
  (dosync
    (alter world-state assoc-in [:puzzles puzzle-key] true)))

(defn check-global-puzzle-required
  "Проверить, выполнены ли требования для глобальной головоломки"
  [puzzle-key]
  (let [required (get-in @world-state [:puzzles puzzle-key :required])]
    (every? #(get-in @world-state [:puzzles %]) required)))



;; СЛУЖЕБНЫЕ ФУНКЦИИ

(defn get-all-players
  "Получить список всех игроков"
  []
  (keys (get-in @world-state [:players])))

(defn get-players-in-room
  "Получить игроков в комнате (кроме указанного)"
  [room-name exclude-player]
  (let [all-players (get-room-players room-name)]
    (disj all-players exclude-player)))

(defn get-available-exits
  "Получить доступные выходы из комнаты"
  [room-name]
  (let [exits (get-room-exits room-name)
        locked (get-in @world-state [:rooms room-name :locked-exits])]
    (reduce-kv (fn [acc dir target]
                 (if (contains? locked dir)
                   (assoc acc dir {:room target :locked true})
                   (assoc acc dir {:room target :locked false})))
               {} exits)))

(defn reset-world!
  "Сбросить мир к начальному состоянию (для тестов)"
  []
  (dosync
    (ref-set world-state (get (var-get #'world-state) :initial-state))))



;; ИНИЦИАЛИЗАЦИЯ

;; Сохраняем начальное состояние для возможного сброса
(dosync
  (alter world-state assoc :initial-state @world-state))

(println "[WORLD] Игровой мир инициализирован с STM системой")
(println "[WORLD] Комнат:" (count (get-in @world-state [:rooms])))
(println "[WORLD] Предметов в реестре:" (count (get-in @world-state [:items-registry])))

;; Экспорт ключевых функций
(comment
  ;; Примеры использования в REPL:
  
  ;; Получить состояние мира
  @world-state
  
  ;; Получить комнату
  (get-room :start)
  
  ;; Добавить игрока
  (add-player! "Алексей")
  
  ;; Проверить выходы
  (get-available-exits :hallway)
  
  ;; Переместить предмет
  (move-item-between-rooms! :start :hallway "ключ-карта")
  
  ;; Проверить головоломку
  (puzzle-solved? :hallway)
  
  ;; STM транзакция вручную
  (dosync
    (alter world-state update-in [:rooms :start :items] conj "новый-предмет"))
)
