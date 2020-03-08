package sh.niall.misty.utils.reminders;

import sh.niall.misty.utils.time.Period;
import sh.niall.yui.exceptions.CommandException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.StrictMath.round;

public class HumanDateConverter {
    static final Set<String> yearPhrases = new HashSet<>(Arrays.asList("years", "year", "yrs", "yr", "y"));
    static final Set<String> monthPhrases = new HashSet<>(Arrays.asList("months", "month", "mths", "mth"));
    static final Set<String> weekPhrases = new HashSet<>(Arrays.asList("weeks", "week", "wks", "wk", "w"));
    static final Set<String> dayPhrases = new HashSet<>(Arrays.asList("days", "day", "d"));
    static final Set<String> hourPhrases = new HashSet<>(Arrays.asList("hours", "hour", "hrs", "hr", "h"));
    static final Set<String> minutePhrases = new HashSet<>(Arrays.asList("minutes", "minute", "mins", "min", "m"));
    static final Set<String> secondPhrases = new HashSet<>(Arrays.asList("seconds", "second", "secs", "sec", "s"));
    static final Set<String> tomorrowPhrases = new HashSet<>(Arrays.asList("tomorrow", "tmmw"));
    static final Set<String> mondayPhrases = new HashSet<>(Arrays.asList("monday", "mon"));
    static final Set<String> tuesdayPhrases = new HashSet<>(Arrays.asList("tuesday", "tu", "tue", "tues"));
    static final Set<String> wednesdayPhrases = new HashSet<>(Arrays.asList("wednesday", "wed"));
    static final Set<String> thursdayPhrases = new HashSet<>(Arrays.asList("thursday", "th", "thu", "thur", "thurs"));
    static final Set<String> fridayPhrases = new HashSet<>(Arrays.asList("friday", "fri"));
    static final Set<String> saturdayPhrases = new HashSet<>(Arrays.asList("saturday", "sat"));
    static final Set<String> sundayPhrases = new HashSet<>(Arrays.asList("sunday", "sun"));
    static final Set<String> otherKeywords = new HashSet<>(Arrays.asList("next", "at", "on", "midnight", "midday"));
    static final Map<String, Month> monthMap = new HashMap<>(Map.ofEntries(
            Map.entry("jan", Month.JANUARY), Map.entry("feb", Month.FEBRUARY), Map.entry("mar", Month.MARCH),
            Map.entry("apr", Month.APRIL), Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("aug", Month.AUGUST), Map.entry("sep", Month.SEPTEMBER),
            Map.entry("oct", Month.OCTOBER), Map.entry("nov", Month.NOVEMBER), Map.entry("dec", Month.DECEMBER)));
    static Set<String> keywords = returnKeywordSet();

    ZoneOffset zoneOffset = ZoneOffset.UTC;
    LocalDateTime ldt = LocalDateTime.now(zoneOffset);
    List<String> foundKeywords = new ArrayList<>();
    int year = ldt.getYear();
    int month = ldt.getMonthValue();
    int day = ldt.getDayOfMonth();
    sh.niall.misty.utils.time.Period period = sh.niall.misty.utils.time.Period.am;
    int hour = 0; // 12 hour
    int minute = ldt.getMinute();
    int second = ldt.getSecond();

    /**
     * Parses the string from human speak to a utc timestamp
     *
     * @param toParse the string to parse
     */
    public HumanDateConverter(String toParse) throws CommandException {
        // First sanitise the input
        sanitiseInput(toParse);
        if (foundKeywords.isEmpty())
            throw new CommandException("No words were found!");

        // Setup hour
        convertTo12HrAndSet(ldt.getHour());

        // Set up for search
        List<Integer> periodIndexes = new ArrayList<>();
        boolean tomorrowExists = false;
        List<Integer> atIndexes = new ArrayList<>();
        List<Integer> nextIndexes = new ArrayList<>();
        List<Integer> onIndexes = new ArrayList<>();
        List<Integer> yearIndexes = new ArrayList<>();
        List<Integer> monthIndexes = new ArrayList<>();
        List<Integer> weekIndexes = new ArrayList<>();
        List<Integer> dayIndexes = new ArrayList<>();
        List<Integer> hourIndexes = new ArrayList<>();
        List<Integer> minuteIndexes = new ArrayList<>();
        List<Integer> secondIndexes = new ArrayList<>();

        // Search for the strings
        int currentIndex = -1;
        for (String word : foundKeywords) {
            currentIndex++;
            if (word.equals("am") || word.equals("pm"))
                periodIndexes.add(currentIndex);
            else if (tomorrowPhrases.contains(word))
                tomorrowExists = true;
            else if (word.equals("at"))
                atIndexes.add(currentIndex);
            else if (word.equals("next"))
                nextIndexes.add(currentIndex);
            else if (word.equals("on"))
                onIndexes.add(currentIndex);
            else if (yearPhrases.contains(word))
                yearIndexes.add(currentIndex);
            else if (monthPhrases.contains(word))
                monthIndexes.add(currentIndex);
            else if (weekPhrases.contains(word))
                weekIndexes.add(currentIndex);
            else if (dayPhrases.contains(word))
                dayIndexes.add(currentIndex);
            else if (hourPhrases.contains(word))
                hourIndexes.add(currentIndex);
            else if (minutePhrases.contains(word))
                minuteIndexes.add(currentIndex);
            else if (secondPhrases.contains(word))
                secondIndexes.add(currentIndex);
        }

        // Run the methods
        if (!periodIndexes.isEmpty())
            handlePeriod(foundKeywords.get(periodIndexes.get(0)));

        if (tomorrowExists)
            handleTomorrow();

        if (!nextIndexes.isEmpty())
            handleNext(nextIndexes);

        if (!onIndexes.isEmpty())
            handleOn(onIndexes);

        if (!atIndexes.isEmpty())
            handleAt(atIndexes);

        if (!yearIndexes.isEmpty() || !monthIndexes.isEmpty() || !weekIndexes.isEmpty() || !dayIndexes.isEmpty() || !hourIndexes.isEmpty() || !minuteIndexes.isEmpty() || !secondIndexes.isEmpty())
            handleUnit(yearIndexes, monthIndexes, weekIndexes, dayIndexes, hourIndexes, minuteIndexes, secondIndexes);
    }

    private void handleUnit(List<Integer> yearIndexes, List<Integer> monthIndexes, List<Integer> weekIndexes, List<Integer> dayIndexes, List<Integer> hourIndexes, List<Integer> minuteIndexes, List<Integer> secondIndexes) throws CommandException {
        long total = 0;
        for (int yearIndex : yearIndexes) {
            try {
                total += TimeUnit.DAYS.toSeconds(Integer.parseInt(foundKeywords.get(yearIndex - 1)) * 365);
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid year amount provided!");
            }
        }
        for (int monthIndex : monthIndexes) {
            try {
                total += TimeUnit.DAYS.toSeconds(round(Integer.parseInt(foundKeywords.get(monthIndex - 1)) * 30.417));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid month amount provided!");
            }
        }
        for (int weekIndex : weekIndexes) {
            try {
                total += TimeUnit.DAYS.toSeconds(Integer.parseInt(foundKeywords.get(weekIndex - 1)) * 7);
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid week amount provided!");
            }
        }
        for (int dayIndex : dayIndexes) {
            try {
                total += TimeUnit.DAYS.toSeconds(Integer.parseInt(foundKeywords.get(dayIndex - 1)));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid day amount provided!");
            }
        }
        for (int hourIndex : hourIndexes) {
            try {
                total += TimeUnit.HOURS.toSeconds(Integer.parseInt(foundKeywords.get(hourIndex - 1)));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid hour amount provided!");
            }
        }
        for (int minuteIndex : minuteIndexes) {
            try {
                total += TimeUnit.MINUTES.toSeconds(Integer.parseInt(foundKeywords.get(minuteIndex - 1)));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid minute amount provided!");
            }
        }
        for (int secondIndex : secondIndexes) {
            try {
                total += TimeUnit.SECONDS.toSeconds(Integer.parseInt(foundKeywords.get(secondIndex - 1)));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                throw new CommandException("Invalid second amount provided!");
            }
        }
        if (total == 0)
            return;

        LocalDateTime localDateTime = LocalDateTime.of(year, month, day, covertTo24Hr(hour), minute, second).plus(total, ChronoUnit.SECONDS);
        setYear(localDateTime.getYear());
        setMonth(localDateTime.getMonthValue());
        setDay(localDateTime.getDayOfMonth());
        convertTo12HrAndSet(localDateTime.getHour());
        setMinute(localDateTime.getMinute());
        setSecond(localDateTime.getSecond());
    }

    private void handlePeriod(String word) {
        if (word.equals("am"))
            period = sh.niall.misty.utils.time.Period.am;
        else
            period = sh.niall.misty.utils.time.Period.pm;
    }

    private void handleTomorrow() throws CommandException {
        LocalDateTime nextDay = ldt.plusDays(1);
        setYear(nextDay.getYear());
        setMonth(nextDay.getMonthValue());
        setDay(nextDay.getDayOfMonth());
        setHour(9);
        setMinute(0);
        setSecond(0);
    }

    private void handleAt(List<Integer> indexes) throws CommandException {
        for (int index : indexes) {
            try {
                String possibleTime = foundKeywords.get(index + 1);
                if (possibleTime.contains(":")) {
                    try {
                        String[] splitString = possibleTime.split(":");
                        setHour(Integer.parseInt(splitString[0]));
                        setMinute(Integer.parseInt(splitString[1]));
                        setSecond(Integer.parseInt(splitString[2]));
                        return;
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        return;
                    }
                } else if (possibleTime.equals("midday")) {
                    setHour(12);
                    setMinute(0);
                    setSecond(0);
                    return;
                } else if (possibleTime.equals("midnight")) {
                    setHour(0);
                    setMinute(0);
                    setSecond(0);
                    return;
                } else {
                    setHour(Integer.parseInt(possibleTime));
                    return;
                }
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            }
        }
    }

    private void handleNext(List<Integer> indexes) throws CommandException {
        for (int index : indexes) {
            try {
                handleDayOfWeek(foundKeywords.get(index + 1));
                return;
            } catch (IndexOutOfBoundsException | CommandException ignored) {
            }
        }
        throw new CommandException("`next` was specified without a valid day!");
    }

    private void handleOn(List<Integer> indexes) throws CommandException {
        for (int index : indexes) {
            // For this we need to test the next index.
            if (foundKeywords.size() <= index + 1)
                continue;

            String possibleDate = foundKeywords.get(index + 1);
            // Check if it matches a date format eg 10/10/2020
            if (possibleDate.matches("^([0-2][0-9]|(3)[0-1])(/)(((0)[0-9])|((1)[0-2]))(/)(\\d{4}|\\d{2})$")) {
                String[] splitDate = possibleDate.split("/");
                setDay(Integer.parseInt(splitDate[0]));
                setMonth(Integer.parseInt(splitDate[1]));
                handleYear(splitDate[2]);

                // Check for long form date
            } else if (possibleDate.matches("\\d+")) {
                setDay(Integer.parseInt(possibleDate));
                setHour(9);
                setMinute(0);
                setSecond(0);
                if (foundKeywords.size() <= index + 2)
                    continue;

                String possibleMonth = foundKeywords.get(index + 2);
                found:
                {
                    for (Map.Entry<String, Month> monthEntry : monthMap.entrySet()) {
                        if (possibleMonth.startsWith(monthEntry.getKey())) {
                            handleMonth(monthEntry.getValue());
                            break found;
                        }
                    }
                    throw new CommandException("Invalid Month provided after `on`. Please provide a a validate date like `on 10/10/2020`, `on 10th jan` or `20 July 2020`");
                }

                // Check if year was provided
                if (foundKeywords.size() > index + 3 && foundKeywords.get(index + 3).matches("(\\d{4}|\\d{2})"))
                    handleYear(foundKeywords.get(index + 3));
            } else {
                // Check if
                try {
                    handleDayOfWeek(foundKeywords.get(index + 1));
                } catch (CommandException ignored) {
                }
            }
        }
    }

    private void handleYear(String year) throws CommandException {
        if (year.length() == 2)
            setYear(Integer.parseInt(String.valueOf(ldt.getYear()).substring(0, 2) + year));
        else
            setYear(Integer.parseInt(year));
    }

    private void handleMonth(Month month) throws CommandException {
        if (month.getValue() < ldt.getMonthValue())
            setYear(ldt.getYear() + 1);
        else
            setYear(ldt.getYear());
        setMonth(month.getValue());
    }

    private void handleDayOfWeek(String dayString) throws CommandException {
        DayOfWeek day;
        Character firstLetter = dayString.charAt(0);
        if (firstLetter.equals('m') && mondayPhrases.contains(dayString))
            day = DayOfWeek.MONDAY;
        else if (firstLetter.equals('t') && tuesdayPhrases.contains(dayString))
            day = DayOfWeek.TUESDAY;
        else if (firstLetter.equals('w') && wednesdayPhrases.contains(dayString))
            day = DayOfWeek.WEDNESDAY;
        else if (firstLetter.equals('t') && thursdayPhrases.contains(dayString))
            day = DayOfWeek.THURSDAY;
        else if (firstLetter.equals('f') && fridayPhrases.contains(dayString))
            day = DayOfWeek.FRIDAY;
        else if (firstLetter.equals('s') && saturdayPhrases.contains(dayString))
            day = DayOfWeek.SATURDAY;
        else if (firstLetter.equals('s') && sundayPhrases.contains(dayString))
            day = DayOfWeek.SUNDAY;
        else
            throw new CommandException("Invalid day of the week provided!");

        LocalDateTime next = ldt.with(TemporalAdjusters.next(day));
        setYear(next.getYear());
        setMonth(next.getMonthValue());
        setDay(next.getDayOfMonth());
        setHour(9);
        setMinute(0);
        setSecond(0);
    }

    private void sanitiseInput(String input) {
        String cleanInput = input
                .toLowerCase()
                .replaceAll("am\\b", " am ")
                .replaceAll("pm\\b", " pm ")
                .replaceAll("(?<=\\d)(st|nd|rd|th)", "")
                .trim();
        String[] splitInput = cleanInput.split(" ");

        for (String word : splitInput) {
            if (keywords.contains(word)
                    || word.matches("\\d+")
                    || word.matches("^([0-9]|([1][0-2])):[0-5]?[0-9]:?[0-5]?[0-9]?")
                    || word.matches("(\\b\\d{1,2}\\D{0,3})?\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|(nov|dec)(?:ember)?)")
                    || word.matches("^([0-2][0-9]|(3)[0-1])(/)(((0)[0-9])|((1)[0-2]))(/)(\\d{4}|\\d{2})$")) {
                foundKeywords.add(word);
            }
        }
    }

    private void setYear(int year) throws CommandException {
        if (year < ldt.getYear())
            throw new CommandException("Year in the past was specified!");
        this.year = year;
    }

    private void setMonth(int month) throws CommandException {
        if (12 < month || month < 1)
            throw new CommandException("Month specified doesn't exist!");
        this.month = month;
    }

    private void setDay(int day) throws CommandException {
        if (31 < day || day < 1)
            throw new CommandException("Day specified doesn't exist!");
        this.day = day;
    }

    private void setHour(int hour) throws CommandException {
        if (12 < hour || hour < 1)
            throw new CommandException("Hour specified doesn't exist!");
        this.hour = hour;
    }

    private void setMinute(int minute) throws CommandException {
        if (59 < minute || minute < 0)
            throw new CommandException("Minute specified doesn't exist!");
        this.minute = minute;
    }

    private void setSecond(int second) throws CommandException {
        if (59 < minute || minute < 0)
            throw new CommandException("Second specified doesn't exist!");
        this.second = second;
    }

    private int covertTo24Hr(int toConvert) {
        String hr24S = String.valueOf(toConvert);
        if (hr24S.length() == 1)
            hr24S = "0" + hr24S;
        return Integer.parseInt(LocalTime.parse(hr24S + period.toString(), DateTimeFormatter.ofPattern("hha")).format(DateTimeFormatter.ofPattern("HH")));
    }

    private void convertTo12HrAndSet(int toConvert) throws CommandException {
        String hr24S = String.valueOf(toConvert);
        if (hr24S.length() == 1)
            hr24S = "0" + hr24S;
        LocalTime localTime = LocalTime.parse(hr24S, DateTimeFormatter.ofPattern("HH"));
        setHour(Integer.parseInt(localTime.format(DateTimeFormatter.ofPattern("hh"))));
        handlePeriod(localTime.format(DateTimeFormatter.ofPattern("a")));
    }

    public long toTimestamp() throws CommandException {
        LocalDateTime newDateTime = LocalDateTime.of(year, month, day, covertTo24Hr(hour), minute, second);
        if (!newDateTime.equals(ldt))
            return newDateTime.toEpochSecond(zoneOffset);
        throw new CommandException("Please specify when to remind you!");
    }

    private static Set<String> returnKeywordSet() {
        Set<String> output = new HashSet<>();
        Stream.of(yearPhrases, mondayPhrases, weekPhrases, dayPhrases, hourPhrases, minutePhrases, secondPhrases,
                tomorrowPhrases, mondayPhrases, tuesdayPhrases, wednesdayPhrases, thursdayPhrases, fridayPhrases,
                saturdayPhrases, sundayPhrases, otherKeywords).forEach(output::addAll);
        output.add(Period.am.name());
        output.add(Period.pm.name());
        return output;
    }
}
