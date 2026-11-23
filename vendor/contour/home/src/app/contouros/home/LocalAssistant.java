package app.contouros.home;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

/** Typed, local executor. Screen content and model text never reach this entry point automatically. */
final class LocalAssistant {
    static String reply(Context c, String input) {
        OfflineCommand command = OfflineCommand.parse(input);
        android.content.SharedPreferences state = c.getSharedPreferences("assistant_command", Context.MODE_PRIVATE);
        long pending = state.getLong("awaiting_time", 0);
        long age = System.currentTimeMillis() - pending;
        if (command.kind == OfflineCommand.Kind.UNKNOWN && pending > 0 && age >= 0 && age < 120000) command = OfflineCommand.timeReply(input);
        if (command.kind != OfflineCommand.Kind.NEED_TIME) state.edit().remove("awaiting_time").apply();
        switch (command.kind) {
            case DELAY: case AT:
                try {
                    AssistantAlarm.Record r = AssistantAlarm.schedule(c, command.triggerAt(System.currentTimeMillis(), ZoneId.systemDefault()));
                    return "Будильник запланирован на " + time(r.at) + ". Можешь закрыть диалог и погасить экран.";
                } catch (IllegalStateException | IllegalArgumentException e) {
                    return e.getMessage() == null ? "Не получилось поставить будильник." : e.getMessage();
                } catch (RuntimeException e) { return "Android отклонил постановку будильника. Проверь доступ внизу экрана."; }
            case NEED_TIME:
                state.edit().putLong("awaiting_time", System.currentTimeMillis()).apply();
                return "На какое время? Напиши, например, «через пять минут» или «на 7:30». Могу поставить в пределах суток.";
            case CANCEL:
                AssistantAlarm.Record r = AssistantAlarm.read(c);
                if (!r.active()) return "У меня сейчас нет активного будильника. Будильники из других приложений я не меняла.";
                return AssistantAlarm.cancel(c, r.id) ? "Мой будильник отменён." : "Звук остановлен, но не удалось сохранить отмену. Проверь состояние будильника.";
            case STATUS: return status(c);
            case LEAVE:
                if (AssistantPresence.read(c) == PresenceMode.ALONE) return "Хорошо. Побуду в углу.";
                AssistantPresence.set(c, PresenceMode.ASKING);
                return AssistantPresence.PERMISSION_QUESTION;
            case RETURN:
                AssistantPresence.set(c, PresenceMode.HERE);
                return "Я здесь.";
            case YES: case NO:
                return answer(c, command.kind == OfflineCommand.Kind.YES);
            case SOUND:
                return AssistantPresence.soundAllowed(c)
                        ? "Сейчас звук разрешён. Чтобы я отвечала без звука, включи на телефоне беззвучный режим или вибрацию."
                        : "Сейчас я отвечаю без звука. Звонок будильника зависит от громкости будильников.";
            case STOP: return stop(c);
            case GREETING: return "Привет. Я здесь. Давай попробуем что-нибудь простое — например, будильник через минуту.";
            case THANKS: return "Пожалуйста. Если понадоблюсь — я рядом.";
            default: return "Этот запрос я пока не умею выполнять. Сейчас можно написать: «поставь будильник через минуту», «поставь будильник на 7:30», «отмени будильник» или «стоп».";
        }
    }
    private static String answer(Context c, boolean yes) {
        PresenceMode mode = AssistantPresence.read(c);
        if (mode == PresenceMode.ASKING) {
            AssistantPresence.set(c, yes ? PresenceMode.WAIT_FOR_CHARGE : PresenceMode.ALONE);
            if (yes) AssistantPresence.observe(c);
            return yes ? "Договорились. Если замечу низкий заряд, предложу помощь." : "Хорошо. Позови, когда захочешь.";
        }
        if (mode == PresenceMode.BATTERY_OFFER) {
            if (AssistantPresence.observe(c) != mode) return "Эта подсказка уже не нужна.";
            AssistantPresence.set(c, PresenceMode.HERE);
            if (!yes) return "Хорошо.";
            try {
                c.startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                return "Открыла настройки энергосбережения.";
            } catch (ActivityNotFoundException | SecurityException e) {
                return "Энергосбережение можно включить в разделе «Батарея» системных настроек.";
            }
        }
        return "Уточни, пожалуйста, что ты имеешь в виду.";
    }

    static String stop(Context c) {
        PresenceMode mode = AssistantPresence.read(c);
        AssistantPresence.set(c, mode.isAway() ? PresenceMode.ALONE : PresenceMode.HERE);
        c.getSharedPreferences(QuietTile.PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(QuietTile.QUIET, true).apply();
        AssistantAlarm.Record r = AssistantAlarm.read(c);
        if (AssistantAlarm.RINGING.equals(r.state)) {
            return AssistantAlarm.cancel(c, r.id) ? "Остановилась. Звонок выключен." : "Звук остановлен. Сохранение отмены завершилось ошибкой.";
        }
        return "Остановилась. Сейчас выполняемых действий нет. Будущий будильник отменяется отдельной просьбой.";
    }
    static String status(Context c) {
        AssistantAlarm.Record r = AssistantAlarm.read(c);
        switch (r.state) {
            case AssistantAlarm.WAITING: return "Мой будильник: " + time(r.at);
            case AssistantAlarm.RINGING: return "Будильник срабатывает. Напиши «стоп» или нажми кнопку остановки.";
            case AssistantAlarm.FAILED: return "Последний будильник завершился ошибкой. Проверь разрешения и звук.";
            case AssistantAlarm.MISSED: return "Последний будильник пропущен или звонок завершился без ответа.";
            default: return "У меня нет активного будильника.";
        }
    }
    static String time(long at) { return new SimpleDateFormat("HH:mm:ss · d MMM", Locale.getDefault()).format(new Date(at)); }
}
