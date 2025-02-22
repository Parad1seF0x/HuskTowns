package net.william278.husktowns;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.google.common.collect.ImmutableList;
import net.william278.husktowns.audit.Action;
import net.william278.husktowns.claim.*;
import net.william278.husktowns.map.MapSquare;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.user.BukkitUser;
import net.william278.husktowns.user.OnlineUser;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

@DisplayName("Plugin Tests")
public class BukkitPluginTests {

    private static ServerMock server;
    private static BukkitHuskTowns plugin;

    @BeforeAll
    @DisplayName("Test Plugin Initialization")
    public static void setUpPlugin() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(BukkitHuskTowns.class);
    }

    @AfterAll
    @DisplayName("Tear down Plugin")
    public static void tearDownPlugin() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Data Validation Tests")
    public class ValidationTests {

        @ParameterizedTest(name = "{index} (Name: {0})")
        @DisplayName("Test Good Names Pass")
        @MethodSource("getTestTownNames")
        public void testTownNameIsValid(@NotNull String name) {
            Assertions.assertTrue(plugin.getValidator().isLegalTownName(name));
        }

        @ParameterizedTest(name = "{index} (Name: {0})")
        @DisplayName("Test Bad Names Fail")
        @MethodSource("getBadTownNames")
        public void testTownNameIsInvalid(@NotNull String name) {
            Assertions.assertFalse(plugin.getValidator().isLegalTownName(name));
        }

        @NotNull
        private static List<String> getBadTownNames() {
            return BukkitPluginTests.getBadTownNames();
        }

        @NotNull
        private static List<String> getTestTownNames() {
            return BukkitPluginTests.getTestTownNames();
        }
    }

    @Nested
    @DisplayName("Level Limit Tests")
    public class LevelLimitTests {

        @ParameterizedTest(name = "To Lv: {index} (Need: {0}, From Lv: {1})")
        @DisplayName("Test Level Up Cost Calculation")
        @MethodSource("getLevelUpCostArguments")
        public void testLevelUpCostCalculation(@NotNull BigDecimal money, int currentLevel) {
            Assertions.assertEquals(money.longValueExact(), plugin.getLevels().getLevelUpCost(currentLevel).longValueExact());
        }

        @NotNull
        private static Stream<Arguments> getLevelUpCostArguments() {
            return Stream.iterate(1, i -> i + 1)
                    .limit(20)
                    .map(i -> Arguments.of(BigDecimal.valueOf(Math.pow(2D, i) * 1000D), i - 1));
        }

    }

    @Nested
    @DisplayName("Town Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public class TownTests {

        @Order(1)
        @ParameterizedTest(name = "{index} (Town Name: {0})")
        @DisplayName("Test Town Creation")
        @MethodSource("getTownCreationParameters")
        public void testTownCreation(@NotNull String name, @NotNull Player creator) {
            final Town town = plugin.getDatabase().createTown(name, BukkitUser.adapt(creator));
            Assertions.assertNotNull(town);
            plugin.getTowns().add(town);
        }

        @Order(2)
        @ParameterizedTest(name = "Town ID: {index}")
        @DisplayName("Test Town Member Addition")
        @MethodSource("getTownAndMayorParameters")
        public void testTownMemberAddition(@NotNull Town town, @NotNull Player mayor) {
            final Player playerToAdd = makePlayer();
            town.addMember(playerToAdd.getUniqueId(), plugin.getRoles().getDefaultRole());
            plugin.getManager().updateTownData(BukkitUser.adapt(mayor), town);
            Assertions.assertTrue(town.getMembers().containsKey(playerToAdd.getUniqueId()));
            Assertions.assertEquals(plugin.getRoles().getDefaultRole().getWeight(),
                    town.getMembers().get(playerToAdd.getUniqueId()));
        }

        @Order(3)
        @ParameterizedTest(name = "Town ID: {index}")
        @DisplayName("Test Town Claiming")
        @MethodSource("getTownAndMayorParameters")
        public void testTownClaiming(@NotNull Town town, @NotNull Player player) {
            final int townIndex = getTestTownNames().indexOf(town.getName());
            final Location location = player.getLocation();
            player.teleport(location.add(224D * townIndex, 0, 0));

            final Chunk chunk = Chunk.at(location.getChunk().getX(), location.getChunk().getZ());
            final TownClaim townClaim = new TownClaim(town, Claim.at(chunk));
            final OnlineUser claimer = BukkitUser.adapt(player);

            town.setClaimCount(town.getClaimCount() + 1);
            town.getLog().log(Action.of(claimer, Action.Type.CREATE_CLAIM, townClaim.claim().toString()));
            plugin.getManager().updateTownData(claimer, town);

            final org.bukkit.World world = location.getWorld();
            Assertions.assertNotNull(world);

            final World pluginWorld = World.of(world.getUID(), world.getName(), world.getEnvironment().name());
            final Optional<ClaimWorld> claimWorld = plugin.getClaimWorld(pluginWorld);
            Assertions.assertTrue(claimWorld.isPresent());

            claimWorld.get().addClaim(townClaim);
            plugin.getDatabase().updateClaimWorld(claimWorld.get());
            Assertions.assertTrue(claimWorld.get().getClaims().containsKey(town.getId()));

            final Optional<TownClaim> claim = plugin.getClaimAt(chunk, pluginWorld);
            Assertions.assertTrue(claim.isPresent());
        }

        @Order(4)
        @ParameterizedTest(name = "Town ID: {index}")
        @DisplayName("Test Claim Map Building")
        @MethodSource("getTownAndMayorParameters")
        public void testClaimMapBuilding(@NotNull Town town, @NotNull Player player) {
            final Chunk chunk = Chunk.at(player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ());
            final World world = World.of(player.getWorld().getUID(), player.getWorld().getName(), player.getWorld().getEnvironment().name());
            final Optional<TownClaim> optionalClaim = plugin.getClaimAt(chunk, world);
            Assertions.assertTrue(optionalClaim.isPresent());

            final TownClaim claim = optionalClaim.get();
            Assertions.assertEquals(claim.town(), town);

            final MapSquare square = MapSquare.claim(chunk, world, claim, plugin);
            Assertions.assertNotNull(square.toComponent().color());
            Assertions.assertEquals(Objects.requireNonNull(square.toComponent().color()).asHexString(), town.getColorRgb());
        }

        @NotNull
        private static Stream<Arguments> getTownCreationParameters() {
            return getTestTownNames().stream()
                    .map(name -> Arguments.of(name, makePlayer()));
        }

        @NotNull
        private static Stream<Arguments> getTownAndMayorParameters() {
            return ImmutableList.copyOf(plugin.getTowns()).stream()
                    .map(town -> Arguments.of(town, server.getOnlinePlayers().stream()
                            .filter(user -> user.getUniqueId().equals(town.getMayor()))
                            .findFirst().orElseThrow()));
        }
    }

    @NotNull
    private static Player makePlayer() {
        return server.addPlayer();
    }

    @NotNull
    private static List<String> getBadTownNames() {
        return readTestData("bad_town_names.txt");
    }

    @NotNull
    private static List<String> getTestTownNames() {
        return readTestData("test_town_names.txt");
    }

    @NotNull
    private static List<String> readTestData(@NotNull String fileName) {
        final List<String> townNames = new ArrayList<>();
        try (Scanner scanner = new Scanner(Objects.requireNonNull(BukkitPluginTests.class.getClassLoader()
                .getResourceAsStream(fileName)))) {
            while (scanner.hasNextLine()) {
                townNames.add(scanner.nextLine());
            }
        }
        return townNames;
    }
}
