package app.contouros.home;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline grammar for direct requests; arbitrary quoted or negated text is never executed. */
final class OfflineCommand {
    enum Kind { DELAY, AT, NEED_TIME, CANCEL, STATUS, GREETING, THANKS, LEAVE, RETURN, YES, NO, SOUND, STOP, UNKNOWN }
    final Kind kind;
    final int minutes, hour, minute;
    private OfflineCommand(Kind kind,int minutes,int hour,int minute){this.kind=kind;this.minutes=minutes;this.hour=hour;this.minute=minute;}
    private static final String REQUEST="(?:(?:поставь|установи|заведи)(?: мне)?(?:,? пожалуйста)? будильник|будильник|(?:разбуди|буди)(?: меня)?)";
    private static final Pattern DELAY=Pattern.compile("(?:"+REQUEST+" через (.+)|через (.+) "+REQUEST+")");
    private static final Pattern AT=Pattern.compile(REQUEST+" (?:на|в) ([0-9]{1,2})[:.]([0-9]{2})");
    private static final Pattern QUANTITY=Pattern.compile("(?:(.+?) )?(минут[ауы]?|час(?:а|ов)?)");
    static String normalize(String input){
        return Normalizer.normalize(input,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е').trim()
            .replaceAll("\\s+"," ").replaceAll("[.!?]+$","")
            .replaceFirst("^пожалуйста[, ]+","").replaceFirst("[, ]+пожалуйста$","").trim();
    }
    static OfflineCommand parse(String input){
        if(input==null||input.length()>1000)return simple(Kind.UNKNOWN);
        String text=normalize(input);
        if (text.matches("нет|без подсказок|не надо|не сейчас")) return simple(Kind.NO);
        if(text.matches(".*(?:^| )не(?: |$).*"))return simple(Kind.UNKNOWN);
        Matcher m=DELAY.matcher(text);
        if(m.matches()){
            int minutes=duration(m.group(1)!=null?m.group(1):m.group(2));
            if(minutes>=1&&minutes<=1440)return new OfflineCommand(Kind.DELAY,minutes,0,0);
            return simple(Kind.NEED_TIME);
        }
        m=AT.matcher(text);
        if(m.matches()){
            int h=Integer.parseInt(m.group(1)),min=Integer.parseInt(m.group(2));
            return h<24&&min<60?new OfflineCommand(Kind.AT,0,h,min):simple(Kind.NEED_TIME);
        }
        if(text.matches("(?:отмени|отключи|убери)(?: мой| этот| свой| последний)? будильник"
                +"|(?:отмени|отключи|убери) будильники"))return simple(Kind.CANCEL);
        if(text.matches("будильники?|какой у меня будильник|когда (?:стоит |мой )?будильник"
                +"|что с будильником|статус будильника|покажи (?:мой )?будильник(?:и)?"))return simple(Kind.STATUS);
        if(text.matches(REQUEST+"(?: (?:на|в) [а-я0-9: .]+)?"))return simple(Kind.NEED_TIME);
        if(text.matches("привет|здравствуй|здравствуйте|доброе утро|добрый вечер"))return simple(Kind.GREETING);
        if(text.matches("спасибо|благодарю"))return simple(Kind.THANKS);
        if(text.matches("оставь меня(?: в покое)?"))return simple(Kind.LEAVE);
        if(text.matches("вернись|возвращайся|побудь рядом|позвать обратно"))return simple(Kind.RETURN);
        if(text.matches("да|да,? можно"))return simple(Kind.YES);
        if(text.matches("побудь тихо|звук помощницы"))return simple(Kind.SOUND);
        if(text.matches("стоп(?:,? возвращаю управление)?|stop"))return simple(Kind.STOP);
        return simple(Kind.UNKNOWN);
    }
    static OfflineCommand timeReply(String input){
        if(input==null||input.length()>100)return simple(Kind.UNKNOWN);
        String text=normalize(input);
        if(text.matches("[0-9]{1,2}[:.][0-9]{2}"))text="на "+text;
        if(text.startsWith("через ")||text.startsWith("на ")||text.startsWith("в "))return parse("будильник "+text);
        int minutes=duration(text);
        return minutes>=1&&minutes<=1440?new OfflineCommand(Kind.DELAY,minutes,0,0):simple(Kind.UNKNOWN);
    }
    private static int duration(String text){
        if(text.equals("полчаса")||text.equals("пол часа"))return 30;
        if(text.equals("полтора часа"))return 90;
        if(text.equals("четверть часа"))return 15;
        Matcher combined=Pattern.compile("(.*?час(?:а|ов)?) (?:и )?(.+ минут[ауы]?)").matcher(text);
        if(combined.matches()){
            int hours=quantity(combined.group(1)),minutes=quantity(combined.group(2));
            return hours>0&&minutes>0&&minutes<60?hours+minutes:-1;
        }
        return quantity(text);
    }
    private static int quantity(String text){
        Matcher m=QUANTITY.matcher(text);if(!m.matches())return -1;
        int n=m.group(1)==null?1:number(m.group(1));
        return n<1?-1:m.group(2).startsWith("час")?n*60:n;
    }
    private static int number(String text){
        if(text.matches("[0-9]{1,4}"))return Integer.parseInt(text);
        String[] units={"ноль","один","два","три","четыре","пять","шесть","семь","восемь","девять","десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать"};
        String[] tens={"двадцать","тридцать","сорок","пятьдесят","шестьдесят","семьдесят","восемьдесят","девяносто"};
        if(text.equals("одну")||text.equals("одна"))return 1;
        if(text.equals("две"))return 2;
        for(int i=0;i<units.length;i++)if(text.equals(units[i]))return i;
        for(int i=0;i<tens.length;i++){
            if(text.equals(tens[i]))return (i+2)*10;
            if(text.startsWith(tens[i]+" ")){int n=number(text.substring(tens[i].length()+1));return n>0&&n<10?(i+2)*10+n:-1;}
        }
        return -1;
    }
    private static OfflineCommand simple(Kind kind){return new OfflineCommand(kind,0,0,0);}
    long triggerAt(long now, ZoneId zone) {
        if (kind == Kind.DELAY) return Math.addExact(now, minutes * 60000L);
        if (kind != Kind.AT) throw new IllegalStateException("Not an alarm command");
        LocalDateTime local = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, minute);
        for (int day = 0; day < 3; day++, local = local.plusDays(1)) {
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
            long best = Long.MAX_VALUE;
            if (offsets.isEmpty()) best = local.atZone(zone).toInstant().toEpochMilli();
            for (ZoneOffset offset : offsets) {
                long candidate = local.toInstant(offset).toEpochMilli();
                if (candidate > now) best = Math.min(best, candidate);
            }
            if (best != Long.MAX_VALUE && best > now) return best;
        }
        throw new IllegalStateException("No future occurrence");
    }
}
