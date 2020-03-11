package sh.niall.misty.utils.reminders;

import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.yui.exceptions.CommandException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    List<String> foundKeywords = new ArrayList<>();
    UserSettings userSettings;
    ZonedDateTime zonedDateTime;
    ZonedDateTime originalZonedDateTime;

    /**
     * Parses the string from human speak to a utc timestamp
     *
     * @param toParse the string to parse
     */
    public HumanDateConverter(UserSettings userSettings, String toParse) throws CommandException {
        this.userSettings = userSettings;
        zonedDateTime = ZonedDateTime.now(userSettings.timezone);
        originalZonedDateTime = zonedDateTime;

        // First sanitise the input
        sanitiseInput(toParse);
        if (foundKeywords.isEmpty())
            throw new CommandException("No words were found!");

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

        if (tomorrowExists)
            handleTomorrow();

        if (!nextIndexes.isEmpty())
            handleNext(nextIndexes);

        if (!onIndexes.isEmpty())
            handleOn(onIndexes);

        if (!atIndexes.isEmpty())
            handleAt(atIndexes);

        if (!periodIndexes.isEmpty())
            handlePeriod(foundKeywords.get(periodIndexes.get(0)));

        if (!yearIndexes.isEmpty() || !monthIndexes.isEmpty() || !weekIndexes.isEmpty() || !dayIndexes.isEmpty() || !hourIndexes.isEmpty() || !minuteIndexes.isEmpty() || !secondIndexes.isEmpty())
            handleUnit(yearIndexes, monthIndexes, weekIndexes, dayIndexes, hourIndexes, minuteIndexes, secondIndexes);
    }

    private void handlePeriod(String word) {
        // First get the current hour into 12 hour time
        int ogHour = zonedDateTime.getHour();
        String hour = ((int) (Math.log10(ogHour) + 1) == 1) ? "0" + ogHour : String.valueOf(ogHour);
        String result = LocalTime.parse(hour, DateTimeFormatter.ofPattern("HH")).format(DateTimeFormatter.ofPattern("hh"));

        // Convert back to 24 hour
        int hour24 = Integer.parseInt(LocalTime.parse(result + word, DateTimeFormatter.ofPattern("hha")).format(DateTimeFormatter.ofPattern("HH")));
        setTime(hour24, zonedDateTime.getMinute(), zonedDateTime.getSecond());
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

        zonedDateTime = zonedDateTime.plusSeconds(total);
    }

    private void handleTomorrow() {
        zonedDateTime = zonedDateTime.plusDays(1);
        setTime(9, 0, 0);
    }

    private void handleAt(List<Integer> indexes) throws CommandException {
        for (int index : indexes) {
            try {
                String possibleTime = foundKeywords.get(index + 1);
                if (possibleTime.contains(":")) {
                    String[] splitString = possibleTime.split(":");
                    if (splitString.length == 2) {
                        setTime(Integer.parseInt(splitString[0]), Integer.parseInt(splitString[1]), 0);
                        return;
                    } else if (splitString.length > 2) {
                        setTime(Integer.parseInt(splitString[0]), Integer.parseInt(splitString[1]), Integer.parseInt(splitString[2]));
                        return;
                    }
                } else if (possibleTime.equals("midday")) {
                    setTime(12, 0, 0);
                    return;
                } else if (possibleTime.equals("midnight")) {
                    setTime(0, 0, 0);
                    return;
                } else {
                    setTime(Integer.parseInt(possibleTime), 0, 0);
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
                setDate(Integer.parseInt(splitDate[0]), Integer.parseInt(splitDate[1]), Integer.parseInt(splitDate[2]));

                // Check for long form date
            } else if (possibleDate.matches("\\d+")) {
                setDate(Integer.parseInt(possibleDate), zonedDateTime.getMonthValue(), zonedDateTime.getYear());
                setTime(9, 0, 0);
                if (foundKeywords.size() <= index + 2)
                    continue;

                String possibleMonth = foundKeywords.get(index + 2);
                found:
                {
                    for (Map.Entry<String, Month> monthEntry : monthMap.entrySet()) {
                        if (possibleMonth.startsWith(monthEntry.getKey())) {
                            if (monthEntry.getValue().getValue() < zonedDateTime.getMonthValue())
                                setDate(zonedDateTime.getDayOfMonth(), monthEntry.getValue().getValue(), zonedDateTime.getYear() + 1);
                            else
                                setDate(zonedDateTime.getDayOfMonth(), monthEntry.getValue().getValue(), zonedDateTime.getYear());
                            break found;
                        }
                    }
                    throw new CommandException("Invalid Month provided after `on`. Please provide a a validate date like `on 10/10/2020`, `on 10th jan` or `20 July 2020`");
                }

                // Check if year was provided
                if (foundKeywords.size() > index + 3 && foundKeywords.get(index + 3).matches("(\\d{4}|\\d{2})"))
                    setDate(zonedDateTime.getDayOfMonth(), zonedDateTime.getMonthValue(), Integer.parseInt(foundKeywords.get(index + 3)));
            } else {
                // Check if
                try {
                    handleDayOfWeek(foundKeywords.get(index + 1));
                } catch (CommandException ignored) {
                }
            }
        }
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

        zonedDateTime = zonedDateTime.with(TemporalAdjusters.next(day));
        setTime(9, 0, 0);
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

    private void setDate(int day, int month, int year) {
        String yr = String.valueOf(year);
        switch (yr.length()) {
            case 1:
                year = Integer.parseInt(String.valueOf(zonedDateTime.getYear()).substring(0, 3) + yr);
                break;
            case 2:
                year = Integer.parseInt(String.valueOf(zonedDateTime.getYear()).substring(0, 2) + yr);
                break;
        }
        zonedDateTime = ZonedDateTime.of(
                year,
                month,
                day,
                zonedDateTime.getHour(),
                zonedDateTime.getMinute(),
                zonedDateTime.getSecond(),
                zonedDateTime.getNano(),
                userSettings.timezone
        );
    }

    private void setTime(int hour, int minute, int second) {
        zonedDateTime = ZonedDateTime.of(
                zonedDateTime.getYear(),
                zonedDateTime.getMonthValue(),
                zonedDateTime.getDayOfMonth(),
                hour,
                minute,
                second,
                0,
                userSettings.timezone
        );
    }

    public long toTimestamp() throws CommandException {
        if (originalZonedDateTime.equals(zonedDateTime))
            throw new CommandException("Please specify when to remind you!");
        return zonedDateTime.toEpochSecond();
    }

    public ZonedDateTime toZonedDateTime() throws CommandException {
        if (originalZonedDateTime.equals(zonedDateTime))
            throw new CommandException("Please specify when to remind you!");
        return zonedDateTime;
    }

    private static Set<String> returnKeywordSet() {
        Set<String> output = new HashSet<>();
        Stream.of(yearPhrases, mondayPhrases, weekPhrases, dayPhrases, hourPhrases, minutePhrases, secondPhrases,
                tomorrowPhrases, mondayPhrases, tuesdayPhrases, wednesdayPhrases, thursdayPhrases, fridayPhrases,
                saturdayPhrases, sundayPhrases, otherKeywords).forEach(output::addAll);
        output.add("am");
        output.add("pm");
        return output;
    }
}
