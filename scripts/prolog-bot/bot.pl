#!/usr/bin/env swipl

:- initialization(main, main).
:- use_module(library(socket)).
:- use_module(library(random)).
:- use_module(library(thread)).

main :-
    sleep(5),  % ждём запуска сервера
    client(localhost, 3333).

client(Host, Port) :-
    setup_call_cleanup(
        tcp_connect(Host:Port, Stream, []),
        (   thread_create(reader_thread(Stream), _, [detached(true)]),
            bot(Stream)
        ),
        close(Stream)
    ).

reader_thread(Stream) :-
    repeat,
    read_line_to_string(Stream, Line),
    (   Line == end_of_file
    ->  true, !
    ;   format('Server: ~s~n', [Line]),
        fail
    ).

send_command(Stream, Command) :-
    format(Stream, '~s~n', [Command]),
    flush_output(Stream),
    sleep(1).

%% Основные действия бота

look_around(Stream) :-
    send_command(Stream, "look").

move_action(Stream) :-
    random_member(Dir, ["north", "south", "east", "west", "east"]), % east чаще
    send_command(Stream, Dir).

chat_message(Stream) :-
    random_member(Message, [
        "Привет от Prolog бота!",
        "Интересная игра...",
        "Кто-нибудь здесь?",
        "Ищу выход из лабиринта",
        "STM в Clojure - это круто!",
    ]),
    format(atom(ChatCmd), "say ~s", [Message]),
    send_command(Stream, ChatCmd).

take_action(Stream) :-
    random_member(Item, ["keycard", "wire", "journal"]),
    format(atom(TakeCmd), "take ~s", [Item]),
    send_command(Stream, TakeCmd).

%% Основной цикл бота
bot(Stream) :-
    % 1. Вводим имя при подключении
    send_command(Stream, "PrologBot"),
    sleep(2),
    
    % 2. Начинаем играть
    send_command(Stream, "look"),
    sleep(3),
    
    % 3. Главный игровой цикл
    repeat,
        random(0.0, 1.0, R),
        (   R < 0.2 -> chat_message(Stream)    % 20% - говорить
        ;   R < 0.6 -> move_action(Stream)     % 40% - двигаться
        ;   R < 0.8 -> look_around(Stream)     % 20% - осмотреться
        ;            -> take_action(Stream)     % 20% - пытаться взять предмет
        ),
        sleep(random_between(3, 7)),  % пауза 3-7 секунд
        fail.  % бесконечный цикл
