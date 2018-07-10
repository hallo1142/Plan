package com.djrapitops.plan.data.store.mutators;

import com.djrapitops.plan.data.container.GeoInfo;
import com.djrapitops.plan.data.container.Session;
import com.djrapitops.plan.data.store.containers.DataContainer;
import com.djrapitops.plan.data.store.containers.PlayerContainer;
import com.djrapitops.plan.data.store.keys.PlayerKeys;
import com.djrapitops.plan.data.store.keys.ServerKeys;
import com.djrapitops.plan.data.store.keys.SessionKeys;
import com.djrapitops.plan.data.store.mutators.formatting.Formatters;
import com.djrapitops.plan.utilities.analysis.AnalysisUtils;
import com.djrapitops.plugin.api.TimeAmount;
import com.djrapitops.plugin.api.utility.log.Log;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Mutator for a bunch of {@link com.djrapitops.plan.data.store.containers.PlayerContainer}s.
 *
 * @author Rsl1122
 */
public class PlayersMutator {

    private List<PlayerContainer> players;

    public PlayersMutator(List<PlayerContainer> players) {
        this.players = players;
    }

    public static PlayersMutator copyOf(PlayersMutator mutator) {
        return new PlayersMutator(new ArrayList<>(mutator.players));
    }

    public static PlayersMutator forContainer(DataContainer container) {
        if (!container.supports(ServerKeys.PLAYERS)) {
            Log.warn(container.getClass().getSimpleName() + " does not support PLAYERS key.");
        }
        return new PlayersMutator(container.getValue(ServerKeys.PLAYERS).orElse(new ArrayList<>()));
    }

    public <T extends Predicate<PlayerContainer>> PlayersMutator filterBy(T by) {
        return new PlayersMutator(players.stream().filter(by).collect(Collectors.toList()));
    }

    public PlayersMutator filterPlayedBetween(long after, long before) {
        return filterBy(
                player -> player.getValue(PlayerKeys.SESSIONS)
                        .map(sessions -> sessions.stream().anyMatch(session -> {
                            long start = session.getValue(SessionKeys.START).orElse(-1L);
                            long end = session.getValue(SessionKeys.END).orElse(-1L);
                            return (after <= start && start <= before) || (after <= end && end <= before);
                        })).orElse(false)
        );
    }

    public PlayersMutator filterRegisteredBetween(long after, long before) {
        return filterBy(
                player -> player.getValue(PlayerKeys.REGISTERED)
                        .map(date -> after <= date && date <= before).orElse(false)
        );
    }

    public PlayersMutator filterRetained(long after, long before) {
        return filterBy(
                player -> {
                    long backLimit = Math.max(after, player.getValue(PlayerKeys.REGISTERED).orElse(0L));
                    long half = backLimit + ((before - backLimit) / 2L);
                    SessionsMutator sessionsMutator = SessionsMutator.forContainer(player);
                    return !sessionsMutator.playedBetween(backLimit, half) && !sessionsMutator.playedBetween(half, before);
                }
        );
    }

    public PlayersMutator filterActive(long date, double limit) {
        return filterBy(player -> player.getActivityIndex(date).getValue() >= limit);
    }

    public List<PlayerContainer> all() {
        return players;
    }

    public List<Long> registerDates() {
        List<Long> registerDates = new ArrayList<>();
        for (PlayerContainer player : players) {
            registerDates.add(player.getValue(PlayerKeys.REGISTERED).orElse(-1L));
        }
        return registerDates;
    }

    public List<String> getGeolocations() {
        List<String> geolocations = new ArrayList<>();

        for (PlayerContainer player : players) {
            Optional<GeoInfo> mostRecent = GeoInfoMutator.forContainer(player).mostRecent();
            mostRecent.ifPresent(geoInfo -> geolocations.add(geoInfo.getGeolocation()));
        }

        return geolocations;
    }

    public TreeMap<Long, Map<String, Set<UUID>>> toActivityDataMap(long date) {
        TreeMap<Long, Map<String, Set<UUID>>> activityData = new TreeMap<>();
        if (!players.isEmpty()) {
            for (PlayerContainer player : players) {
                for (long time = date; time >= date - TimeAmount.MONTH.ms() * 2L; time -= TimeAmount.WEEK.ms()) {
                    ActivityIndex activityIndex = player.getActivityIndex(time);
                    String activityGroup = activityIndex.getGroup();

                    Map<String, Set<UUID>> map = activityData.getOrDefault(time, new HashMap<>());
                    Set<UUID> uuids = map.getOrDefault(activityGroup, new HashSet<>());
                    uuids.add(player.getUnsafe(PlayerKeys.UUID));
                    map.put(activityGroup, uuids);
                    activityData.put(time, map);
                }
            }
        } else {
            activityData.put(date, Collections.emptyMap());
        }
        return activityData;
    }

    public int count() {
        return players.size();
    }

    public int newPerDay() {
        List<Long> registerDates = registerDates();
        int total = 0;
        Function<Long, Integer> formatter = Formatters.dayOfYear();
        Set<Integer> days = new HashSet<>();
        for (Long date : registerDates) {
            int day = formatter.apply(date);
            days.add(day);
            total++;
        }
        int numberOfDays = days.size();

        if (numberOfDays == 0) {
            return 0;
        }
        return total / numberOfDays;
    }

    /**
     * Compares players in the mutator to other players in terms of player retention.
     *
     * @param compareTo Players to compare to.
     * @param dateLimit Epoch ms back limit, if the player registered after this their value is not used.
     * @return Mutator containing the players that are considered to be retained.
     * @throws IllegalStateException If all players are rejected due to dateLimit.
     */
    public PlayersMutator compareAndFindThoseLikelyToBeRetained(Iterable<PlayerContainer> compareTo,
                                                                long dateLimit,
                                                                PlayersOnlineResolver onlineResolver) {
        Collection<PlayerContainer> retainedAfterMonth = new ArrayList<>();
        Collection<PlayerContainer> notRetainedAfterMonth = new ArrayList<>();

        for (PlayerContainer player : players) {
            long registered = player.getValue(PlayerKeys.REGISTERED).orElse(System.currentTimeMillis());

            // Discard uncertain data
            if (registered > dateLimit) {
                continue;
            }

            long monthAfterRegister = registered + TimeAmount.MONTH.ms();
            long half = registered + (TimeAmount.MONTH.ms() / 2L);
            if (player.playedBetween(registered, half) && player.playedBetween(half, monthAfterRegister)) {
                retainedAfterMonth.add(player);
            } else {
                notRetainedAfterMonth.add(player);
            }
        }

        if (retainedAfterMonth.isEmpty() || notRetainedAfterMonth.isEmpty()) {
            throw new IllegalStateException("No players to compare to after rejecting with dateLimit");
        }

        List<RetentionData> retained = retainedAfterMonth.stream()
                .map(player -> new RetentionData(player, onlineResolver))
                .collect(Collectors.toList());
        List<RetentionData> notRetained = notRetainedAfterMonth.stream()
                .map(player -> new RetentionData(player, onlineResolver))
                .collect(Collectors.toList());

        RetentionData avgRetained = AnalysisUtils.average(retained);
        RetentionData avgNotRetained = AnalysisUtils.average(notRetained);

        List<PlayerContainer> toBeRetained = new ArrayList<>();
        for (PlayerContainer player : compareTo) {
            RetentionData retentionData = new RetentionData(player, onlineResolver);
            if (retentionData.distance(avgRetained) < retentionData.distance(avgNotRetained)) {
                toBeRetained.add(player);
            }
        }
        return new PlayersMutator(toBeRetained);
    }

    public List<Session> getSessions() {
        return players.stream()
                .map(player -> player.getValue(PlayerKeys.SESSIONS).orElse(new ArrayList<>()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<UUID> uuids() {
        return players.stream()
                .map(player -> player.getValue(PlayerKeys.UUID).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<PlayerContainer> operators() {
        return players.stream()
                .filter(player -> player.getValue(PlayerKeys.OPERATOR).orElse(false)).collect(Collectors.toList());
    }
}