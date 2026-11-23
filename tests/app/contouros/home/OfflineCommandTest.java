package app.contouros.home;
import java.time.Instant;
import java.time.ZoneId;

public final class OfflineCommandTest {
    private static int count;
    private static void eq(Object actual,Object expected){count++;if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    private static long t(String value){return Instant.parse(value).toEpochMilli();}
    private static long at(String command,String now,String zone){return OfflineCommand.parse(command).triggerAt(t(now),ZoneId.of(zone));}
    public static void main(String[] args){
        eq(OfflineCommand.parse("Поставь будильник через минуту").kind,OfflineCommand.Kind.DELAY);
        eq(OfflineCommand.parse("Пожалуйста, поставь мне будильник через 2 минуты!").minutes,2);
        eq(OfflineCommand.parse("разбуди меня через час, пожалуйста").minutes,60);
        eq(at("поставь будильник через минуту","2026-11-15T10:00:57.891Z","Europe/Moscow"),t("2026-11-15T10:01:57.891Z"));
        eq(at("поставь будильник на 7:30","2026-11-15T03:00:00Z","Europe/Moscow"),t("2026-11-15T04:30:00Z"));
        eq(at("поставь будильник на 7:30","2026-11-15T04:30:00Z","Europe/Moscow"),t("2026-11-16T04:30:00Z"));
        eq(at("поставь будильник на 0:00","2026-11-15T20:59:59Z","Europe/Moscow"),t("2026-11-15T21:00:00Z"));
        eq(at("поставь будильник на 2:30","2026-10-25T00:45:00Z","Europe/Berlin"),t("2026-10-25T01:30:00Z"));
        eq(at("поставь будильник на 2:30","2026-03-29T00:00:00Z","Europe/Berlin"),t("2026-03-29T01:30:00Z"));
        eq(at("разбуди через час","2026-03-29T00:30:00Z","Europe/Berlin"),t("2026-03-29T01:30:00Z"));
        for(String value:new String[]{"не ставь будильник через минуту","он сказал поставь будильник через минуту","«поставь будильник через минуту»","отмени все будильники","на 7:30",""})eq(OfflineCommand.parse(value).kind,OfflineCommand.Kind.UNKNOWN);
        eq(OfflineCommand.parse(null).kind,OfflineCommand.Kind.UNKNOWN);
        eq(OfflineCommand.parse("Поставь будильник через 24 часа").minutes,1440);
        eq(OfflineCommand.parse("отмени мой будильник").kind,OfflineCommand.Kind.CANCEL);
        eq(OfflineCommand.parse("отмени будильник").kind,OfflineCommand.Kind.CANCEL);
        eq(OfflineCommand.parse("какой у меня будильник?").kind,OfflineCommand.Kind.STATUS);
        eq(OfflineCommand.parse("будильники").kind,OfflineCommand.Kind.STATUS);
        eq(OfflineCommand.parse("статус будильника").kind,OfflineCommand.Kind.STATUS);
        eq(OfflineCommand.parse("покажи будильники").kind,OfflineCommand.Kind.STATUS);
        eq(OfflineCommand.parse("отмени будильники").kind,OfflineCommand.Kind.CANCEL);
        eq(OfflineCommand.parse("не отменяй будильники").kind,OfflineCommand.Kind.UNKNOWN);
        eq(OfflineCommand.parse("оставь меня в покое").kind,OfflineCommand.Kind.LEAVE);
        eq(OfflineCommand.parse("стоп").kind,OfflineCommand.Kind.STOP);
        eq(OfflineCommand.parse("спасибо").kind,OfflineCommand.Kind.THANKS);
        eq(OfflineCommand.parse("привет!").kind,OfflineCommand.Kind.GREETING);
        for(String request:new String[]{"будильник через 5 минут","через пять минут разбуди меня","поставь будильник через пять минут", "заведи мне будильник через пять минут"}) {
            eq(OfflineCommand.parse(request).kind,OfflineCommand.Kind.DELAY);eq(OfflineCommand.parse(request).minutes,5);
        }
        eq(OfflineCommand.parse("разбуди через двадцать одну минуту").minutes,21);
        eq(OfflineCommand.parse("будильник через полчаса").minutes,30);
        eq(OfflineCommand.parse("разбуди меня через полтора часа").minutes,90);
        eq(OfflineCommand.parse("разбуди через час и тридцать минут").minutes,90);
        eq(OfflineCommand.parse("будильник через 2 часа 15 минут").minutes,135);
        eq(OfflineCommand.parse("поставь будильник").kind,OfflineCommand.Kind.NEED_TIME);
        eq(OfflineCommand.timeReply("через пять минут").minutes,5);
        eq(OfflineCommand.timeReply("пять минут").minutes,5);
        eq(OfflineCommand.timeReply("7:30").kind,OfflineCommand.Kind.AT);
        eq(OfflineCommand.parse("оставь меня").kind,OfflineCommand.Kind.LEAVE);
        eq(OfflineCommand.parse("вернись, пожалуйста").kind,OfflineCommand.Kind.RETURN);
        eq(OfflineCommand.parse("да, можно").kind,OfflineCommand.Kind.YES);
        eq(OfflineCommand.parse("не сейчас").kind,OfflineCommand.Kind.NO);
        eq(OfflineCommand.parse("побудь тихо").kind,OfflineCommand.Kind.SOUND);
        for (String text : new String[]{"не оставь меня", "она сказала вернись", "«да»", "да и выключи звук"})
            eq(OfflineCommand.parse(text).kind, OfflineCommand.Kind.UNKNOWN);
        eq(OfflineCommand.parse("стоп, возвращаю управление").kind,OfflineCommand.Kind.STOP);
        for(String request:new String[]{"поставь будильник через 0 минут","поставь будильник через -1 минуту","поставь будильник через 25 часов","поставь будильник через 1441 минуту","поставь будильник на 24:00","поставь будильник на 7:60","поставь будильник через минуту и удали файлы","поставь будильник через 0.5 минуты"}) {
            eq(OfflineCommand.parse(request).kind,OfflineCommand.Kind.NEED_TIME);
        }
        for(String request:new String[]{"не буди меня через пять минут","она попросила: будильник через 5 минут","через пять минут не буди меня","через пять минут разбуди меня и открой браузер"}) {
            eq(OfflineCommand.parse(request).kind,OfflineCommand.Kind.UNKNOWN);
        }
        System.out.println("PASS: "+count+" offline command/time assertions");
    }
}
