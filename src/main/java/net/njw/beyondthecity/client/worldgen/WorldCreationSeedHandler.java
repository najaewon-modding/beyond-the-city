package net.njw.beyondthecity.client.worldgen;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.njw.beyondthecity.BeyondtheCity;

@EventBusSubscriber(
        modid = BeyondtheCity.MODID,
        value = Dist.CLIENT
)
public final class WorldCreationSeedHandler {

    private static boolean processed;

    private WorldCreationSeedHandler() {
    }

    @SubscribeEvent
    public static void onScreenInit(
            ScreenEvent.Init.Post event
    ) {
        if (!(event.getScreen()
                instanceof CreateWorldScreen screen)) {
            return;
        }

        if (processed) {
            return;
        }

        /*
         * 사용자가 이미 직접 seed를 입력했다면
         * 덮어쓰지 않는다.
         */
        if (!screen.getUiState()
                .getSeed()
                .isBlank()) {
            return;
        }

        processed = true;

        long seed =
                SeedSearchService.findSuitableSeed(
                        screen.getUiState()
                                .getSettings()
                );

        screen.getUiState()
                .setSeed(
                        Long.toString(seed)
                );

        BeyondtheCity.LOGGER.info(
                "Selected seed {} for Beyond the City",
                seed
        );
    }
}