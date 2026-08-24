package net.njw.beyondthecity.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.city.CityRegion;
import net.njw.beyondthecity.client.ClientCityManager;
import net.njw.beyondthecity.network.CityTeleportRequestPayload;

import java.util.List;

public final class CityListScreen extends Screen {

    /*
     * =========================================================
     * Textures
     * =========================================================
     */

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    BeyondtheCity.MODID,
                    "textures/gui/city_list.png"
            );

    private static final Identifier WIDGET_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    BeyondtheCity.MODID,
                    "textures/gui/city_list_widgets.png"
            );

    /*
     * =========================================================
     * Main GUI
     * =========================================================
     */

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 256;

    /*
     * 실제 화면에서는
     *
     * 256 x 256
     *     ↓ 0.75
     * 192 x 192
     *
     * 로 표시된다.
     */
    private static final float GUI_SCALE = 0.75F;

    /*
     * =========================================================
     * Small Button
     * =========================================================
     *
     * Green / Active:
     *   x = 0
     *   y = 0
     *   86 x 24
     *
     * Gray / Inactive:
     *   x = 86
     *   y = 0
     *   86 x 24
     */

    private static final int SMALL_BUTTON_WIDTH = 86;
    private static final int SMALL_BUTTON_HEIGHT = 24;

    private static final int SMALL_BUTTON_ACTIVE_U = 0;
    private static final int SMALL_BUTTON_INACTIVE_U = 86;
    private static final int SMALL_BUTTON_V = 0;

    /*
     * =========================================================
     * Large Button
     * =========================================================
     *
     * Blue / Selected:
     *   x = 0
     *   y = 24
     *   112 x 37
     *
     * Gray / Unselected:
     *   x = 112
     *   y = 24
     *   112 x 37
     */

    private static final int LARGE_BUTTON_WIDTH = 112;
    private static final int LARGE_BUTTON_HEIGHT = 37;

    private static final int LARGE_BUTTON_SELECTED_U = 0;
    private static final int LARGE_BUTTON_UNSELECTED_U = 112;
    private static final int LARGE_BUTTON_V = 24;

    /*
     * =========================================================
     * Lock Icon
     * =========================================================
     */

    private static final int LOCK_U = 172;
    private static final int LOCK_V = 12;

    private static final int LOCK_WIDTH = 9;
    private static final int LOCK_HEIGHT = 12;

    /*
     * =========================================================
     * City List Area
     * =========================================================
     */

    private static final int LIST_X = 14;

    /*
     * 기존보다 1px 아래.
     */
    private static final int LIST_Y = 39;

    private static final int LIST_ROW_GAP = 2;

    /*
     * 37 * 4 + 2 * 3 = 154
     */
    private static final int LIST_VIEWPORT_HEIGHT = 154;

    private static final int VISIBLE_CITY_COUNT = 4;

    /*
     * =========================================================
     * Detail Area
     * =========================================================
     */

    private static final int DETAIL_X = 138;
    private static final int DETAIL_Y = 46;

    /*
     * =========================================================
     * Bottom Buttons
     * =========================================================
     *
     * 두 버튼:
     *
     * Move  = 86 x 24
     * Close = 86 x 24
     *
     * 86 + 4 + 86 = 176
     *
     * 좌우 여백:
     * (256 - 176) / 2 = 40
     */

    private static final int MOVE_BUTTON_X = 40;
    private static final int CLOSE_BUTTON_X = 130;

    /*
     * 기존 204에서 2px 위.
     */
    private static final int BOTTOM_BUTTON_Y = 202;

    /*
     * =========================================================
     * State
     * =========================================================
     */

    private int selectedIndex = 0;
    private int scrollOffset = 0;

    /*
     * =========================================================
     * Constructor
     * =========================================================
     */

    public CityListScreen() {
        super(
                Component.translatable(
                        "gui.njw_beyond_the_city.city_list.title"
                )
        );
    }

    /*
     * =========================================================
     * Render
     * =========================================================
     */

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        int screenLeft =
                getGuiLeft();

        int screenTop =
                getGuiTop();

        /*
         * 실제 화면 mouse 좌표를
         * 256 x 256 논리 UI 좌표로 변환.
         */
        double logicalMouseX =
                screenToGuiX(
                        mouseX
                );

        double logicalMouseY =
                screenToGuiY(
                        mouseY
                );

        /*
         * =====================================================
         * GUI Transform
         * =====================================================
         *
         * 먼저 화면 중앙 위치로 이동한 후
         * 전체 GUI를 0.75배로 축소한다.
         *
         * 아래 렌더링 코드에서는 다시
         * 0 ~ 255 좌표를 그대로 사용한다.
         */

        graphics.pose().pushMatrix();

        graphics.pose().translate(
                screenLeft,
                screenTop
        );

        graphics.pose().scale(
                GUI_SCALE,
                GUI_SCALE
        );

        /*
         * =====================================================
         * Background
         * =====================================================
         */

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND_TEXTURE,
                0,
                0,
                0,
                0,
                GUI_WIDTH,
                GUI_HEIGHT,
                GUI_WIDTH,
                GUI_HEIGHT
        );

        /*
         * =====================================================
         * City List
         * =====================================================
         */

        renderCityList(
                graphics
        );

        /*
         * =====================================================
         * City Details
         * =====================================================
         */

        renderCityDetails(
                graphics
        );

        /*
         * =====================================================
         * Bottom Buttons
         * =====================================================
         */

        renderBottomButtons(
                graphics,
                logicalMouseX,
                logicalMouseY
        );

        graphics.pose().popMatrix();
    }

    /*
     * =========================================================
     * City List
     * =========================================================
     */

    private void renderCityList(
            GuiGraphicsExtractor graphics
    ) {
        List<ClientCityManager.ClientCity> cities =
                ClientCityManager.getCities();

        normalizeState(
                cities
        );

        /*
         * 현재는 한 번에 최대 4개만 그리기 때문에
         * 목록 밖으로 추가 버튼을 렌더링하지 않는다.
         *
         * 따라서 별도의 scissor 없이도
         * 목록 영역을 넘어가는 버튼은 없다.
         */

        int endIndex =
                Math.min(
                        cities.size(),
                        scrollOffset
                                + VISIBLE_CITY_COUNT
                );

        int row = 0;

        for (
                int index = scrollOffset;
                index < endIndex;
                index++
        ) {
            ClientCityManager.ClientCity city =
                    cities.get(
                            index
                    );

            int x =
                    LIST_X;

            int y =
                    LIST_Y
                            + row
                            * (
                            LARGE_BUTTON_HEIGHT
                                    + LIST_ROW_GAP
                    );

            /*
             * =================================================
             * City Button
             * =================================================
             *
             * Hover가 아니라
             * 선택 여부에 의해서만 디자인이 바뀐다.
             */

            boolean selected =
                    index
                            == selectedIndex;

            int textureU =
                    selected
                            ? LARGE_BUTTON_SELECTED_U
                            : LARGE_BUTTON_UNSELECTED_U;

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    WIDGET_TEXTURE,
                    x,
                    y,
                    textureU,
                    LARGE_BUTTON_V,
                    LARGE_BUTTON_WIDTH,
                    LARGE_BUTTON_HEIGHT,
                    256,
                    256
            );

            /*
             * =================================================
             * Lock Icon
             * =================================================
             */

            if (!city.unlocked()) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        WIDGET_TEXTURE,
                        x + 96,
                        y + 12,
                        LOCK_U,
                        LOCK_V,
                        LOCK_WIDTH,
                        LOCK_HEIGHT,
                        256,
                        256
                );
            }

            /*
             * =================================================
             * City Name
             * =================================================
             */

            int textCenterX =
                    x
                            + LARGE_BUTTON_WIDTH / 2;

            if (!city.unlocked()) {
                textCenterX -= 5;
            }

            graphics.centeredText(
                    font,
                    Component.literal(
                            city.name()
                    ),
                    textCenterX,
                    y + 14,
                    0xFFFFFFFF
            );

            row++;
        }
    }

    /*
     * =========================================================
     * City Details
     * =========================================================
     */

    private void renderCityDetails(
            GuiGraphicsExtractor graphics
    ) {
        ClientCityManager.ClientCity city =
                getSelectedCity();

        int x =
                DETAIL_X;

        int y =
                DETAIL_Y;

        if (city == null) {
            graphics.text(
                    font,
                    Component.translatable(
                            "gui.njw_beyond_the_city.city_list.empty"
                    ),
                    x,
                    y,
                    0xFFAAAAAA,
                    false
            );

            return;
        }

        /*
         * =====================================================
         * City Name
         * =====================================================
         */

        graphics.text(
                font,
                Component.literal(
                        city.name()
                ),
                x,
                y,
                0xFFFFFFFF,
                true
        );

        /*
         * =====================================================
         * Unlock State
         * =====================================================
         *
         * Unlocked = green
         * Locked   = red
         */

        graphics.text(
                font,
                Component.translatable(
                        city.unlocked()
                                ? "gui.njw_beyond_the_city.city_list.status.unlocked"
                                : "gui.njw_beyond_the_city.city_list.status.locked"
                ),
                x,
                y + 18,
                city.unlocked()
                        ? 0xFF7FD35A
                        : 0xFFFF5555,
                true
        );

        /*
         * =====================================================
         * Overworld
         * =====================================================
         */

        CityRegion overworldRegion =
                city.getRegion(
                        Level.OVERWORLD.identifier()
                );

        if (overworldRegion != null) {
            long blockX =
                    (long)
                            overworldRegion.centerChunkX()
                            * 16L;

            long blockZ =
                    (long)
                            overworldRegion.centerChunkZ()
                            * 16L;

            graphics.text(
                    font,
                    Component.translatable(
                            "gui.njw_beyond_the_city.city_list.dimension.overworld"
                    ),
                    x,
                    y + 44,
                    0xFFDDDDDD,
                    false
            );

            graphics.text(
                    font,
                    Component.translatable(
                            "gui.njw_beyond_the_city.city_list.coordinates",
                            blockX,
                            blockZ
                    ),
                    x,
                    y + 56,
                    0xFFFFFFFF,
                    false
            );
        }

        /*
         * =====================================================
         * Nether
         * =====================================================
         */

        CityRegion netherRegion =
                city.getRegion(
                        Level.NETHER.identifier()
                );

        if (netherRegion != null) {
            long blockX =
                    (long)
                            netherRegion.centerChunkX()
                            * 16L;

            long blockZ =
                    (long)
                            netherRegion.centerChunkZ()
                            * 16L;

            graphics.text(
                    font,
                    Component.translatable(
                            "gui.njw_beyond_the_city.city_list.dimension.nether"
                    ),
                    x,
                    y + 78,
                    0xFFDDDDDD,
                    false
            );

            graphics.text(
                    font,
                    Component.translatable(
                            "gui.njw_beyond_the_city.city_list.coordinates",
                            blockX,
                            blockZ
                    ),
                    x,
                    y + 90,
                    0xFFFFFFFF,
                    false
            );
        }
    }

    /*
     * =========================================================
     * Bottom Buttons
     * =========================================================
     */

    private void renderBottomButtons(
            GuiGraphicsExtractor graphics,
            double mouseX,
            double mouseY
    ) {
        ClientCityManager.ClientCity city =
                getSelectedCity();

        /*
         * =====================================================
         * Move
         * =====================================================
         */

        boolean moveEnabled =
                city != null
                        && city.unlocked();

        boolean moveHovered =
                moveEnabled
                        && isInside(
                        mouseX,
                        mouseY,
                        MOVE_BUTTON_X,
                        BOTTOM_BUTTON_Y,
                        SMALL_BUTTON_WIDTH,
                        SMALL_BUTTON_HEIGHT
                );

        /*
         * Move:
         *
         * locked        -> gray
         * enabled idle  -> gray
         * enabled hover -> green
         */

        int moveTextureU =
                moveHovered
                        ? SMALL_BUTTON_ACTIVE_U
                        : SMALL_BUTTON_INACTIVE_U;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                WIDGET_TEXTURE,
                MOVE_BUTTON_X,
                BOTTOM_BUTTON_Y,
                moveTextureU,
                SMALL_BUTTON_V,
                SMALL_BUTTON_WIDTH,
                SMALL_BUTTON_HEIGHT,
                256,
                256
        );

        graphics.centeredText(
                font,
                Component.translatable(
                        "gui.njw_beyond_the_city.city_list.move"
                ),
                MOVE_BUTTON_X
                        + SMALL_BUTTON_WIDTH / 2,
                BOTTOM_BUTTON_Y + 8,
                moveEnabled
                        ? 0xFFFFFFFF
                        : 0xFF777777
        );

        /*
         * =====================================================
         * Close
         * =====================================================
         */

        boolean closeHovered =
                isInside(
                        mouseX,
                        mouseY,
                        CLOSE_BUTTON_X,
                        BOTTOM_BUTTON_Y,
                        SMALL_BUTTON_WIDTH,
                        SMALL_BUTTON_HEIGHT
                );

        /*
         * Close:
         *
         * idle  -> gray
         * hover -> green
         */

        int closeTextureU =
                closeHovered
                        ? SMALL_BUTTON_ACTIVE_U
                        : SMALL_BUTTON_INACTIVE_U;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                WIDGET_TEXTURE,
                CLOSE_BUTTON_X,
                BOTTOM_BUTTON_Y,
                closeTextureU,
                SMALL_BUTTON_V,
                SMALL_BUTTON_WIDTH,
                SMALL_BUTTON_HEIGHT,
                256,
                256
        );

        graphics.centeredText(
                font,
                Component.translatable(
                        "gui.njw_beyond_the_city.city_list.close"
                ),
                CLOSE_BUTTON_X
                        + SMALL_BUTTON_WIDTH / 2,
                BOTTOM_BUTTON_Y + 8,
                0xFFFFFFFF
        );
    }

    /*
     * =========================================================
     * Mouse Click
     * =========================================================
     */

    @Override
    public boolean mouseClicked(
            MouseButtonEvent click,
            boolean doubled
    ) {
        /*
         * 실제 screen 좌표를
         * 256 x 256 내부 GUI 좌표로 역변환한다.
         */

        double mouseX =
                screenToGuiX(
                        click.x()
                );

        double mouseY =
                screenToGuiY(
                        click.y()
                );

        List<ClientCityManager.ClientCity> cities =
                ClientCityManager.getCities();

        /*
         * =====================================================
         * City Button Click
         * =====================================================
         */

        if (
                isInside(
                        mouseX,
                        mouseY,
                        LIST_X,
                        LIST_Y,
                        LARGE_BUTTON_WIDTH,
                        LIST_VIEWPORT_HEIGHT
                )
        ) {
            int endIndex =
                    Math.min(
                            cities.size(),
                            scrollOffset
                                    + VISIBLE_CITY_COUNT
                    );

            int row = 0;

            for (
                    int index = scrollOffset;
                    index < endIndex;
                    index++
            ) {
                int x =
                        LIST_X;

                int y =
                        LIST_Y
                                + row
                                * (
                                LARGE_BUTTON_HEIGHT
                                        + LIST_ROW_GAP
                        );

                if (
                        isInside(
                                mouseX,
                                mouseY,
                                x,
                                y,
                                LARGE_BUTTON_WIDTH,
                                LARGE_BUTTON_HEIGHT
                        )
                ) {
                    selectedIndex =
                            index;

                    return true;
                }

                row++;
            }
        }

        /*
         * =====================================================
         * Move Click
         * =====================================================
         */

        ClientCityManager.ClientCity selectedCity =
                getSelectedCity();

        if (
                selectedCity != null
                        && selectedCity.unlocked()
                        && isInside(
                        mouseX,
                        mouseY,
                        MOVE_BUTTON_X,
                        BOTTOM_BUTTON_Y,
                        SMALL_BUTTON_WIDTH,
                        SMALL_BUTTON_HEIGHT
                )
        ) {
            onMovePressed(
                    selectedCity
            );

            return true;
        }

        /*
         * =====================================================
         * Close Click
         * =====================================================
         */

        if (
                isInside(
                        mouseX,
                        mouseY,
                        CLOSE_BUTTON_X,
                        BOTTOM_BUTTON_Y,
                        SMALL_BUTTON_WIDTH,
                        SMALL_BUTTON_HEIGHT
                )
        ) {
            onClose();

            return true;
        }

        return super.mouseClicked(
                click,
                doubled
        );
    }

    /*
     * =========================================================
     * Mouse Scroll
     * =========================================================
     */

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        List<ClientCityManager.ClientCity> cities =
                ClientCityManager.getCities();

        /*
         * 도시가 4개 이하이면
         * 모든 도시가 이미 표시되므로 스크롤 불필요.
         */

        if (
                cities.size()
                        <= VISIBLE_CITY_COUNT
        ) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollX,
                    scrollY
            );
        }

        /*
         * 화면 좌표 -> 내부 GUI 좌표.
         */

        double logicalMouseX =
                screenToGuiX(
                        mouseX
                );

        double logicalMouseY =
                screenToGuiY(
                        mouseY
                );

        /*
         * 도시 목록 위에 있을 때만
         * 스크롤을 처리한다.
         */

        if (
                !isInside(
                        logicalMouseX,
                        logicalMouseY,
                        LIST_X,
                        LIST_Y,
                        LARGE_BUTTON_WIDTH,
                        LIST_VIEWPORT_HEIGHT
                )
        ) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollX,
                    scrollY
            );
        }

        int maxScroll =
                Math.max(
                        0,
                        cities.size()
                                - VISIBLE_CITY_COUNT
                );

        /*
         * =====================================================
         * Wheel Up
         * =====================================================
         */

        if (scrollY > 0.0) {
            scrollOffset =
                    Math.max(
                            0,
                            scrollOffset - 1
                    );

            return true;
        }

        /*
         * =====================================================
         * Wheel Down
         * =====================================================
         */

        if (scrollY < 0.0) {
            scrollOffset =
                    Math.min(
                            maxScroll,
                            scrollOffset + 1
                    );

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    /*
     * =========================================================
     * Move
     * =========================================================
     */

    private void onMovePressed(
            ClientCityManager.ClientCity city
    ) {
        /*
         * 클라이언트에서는 city id만 서버에 전달한다.
         *
         * 좌표와 unlocked 여부는
         * 서버가 다시 계산 / 검증한다.
         */
        ClientPacketDistributor.sendToServer(
                new CityTeleportRequestPayload(
                        city.id()
                )
        );

        /*
         * 요청을 보낸 뒤 UI를 닫는다.
         */
        onClose();
    }

    /*
     * =========================================================
     * Selected City
     * =========================================================
     */

    private ClientCityManager.ClientCity
    getSelectedCity() {
        List<ClientCityManager.ClientCity> cities =
                ClientCityManager.getCities();

        if (cities.isEmpty()) {
            return null;
        }

        selectedIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedIndex,
                                cities.size() - 1
                        )
                );

        return cities.get(
                selectedIndex
        );
    }

    /*
     * =========================================================
     * State Normalization
     * =========================================================
     */

    private void normalizeState(
            List<ClientCityManager.ClientCity> cities
    ) {
        if (cities.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;

            return;
        }

        selectedIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedIndex,
                                cities.size() - 1
                        )
                );

        int maxScroll =
                Math.max(
                        0,
                        cities.size()
                                - VISIBLE_CITY_COUNT
                );

        scrollOffset =
                Math.max(
                        0,
                        Math.min(
                                scrollOffset,
                                maxScroll
                        )
                );
    }

    /*
     * =========================================================
     * Screen Position
     * =========================================================
     */

    private int getGuiLeft() {
        int renderedWidth =
                Math.round(
                        GUI_WIDTH
                                * GUI_SCALE
                );

        return (
                width
                        - renderedWidth
        ) / 2;
    }

    private int getGuiTop() {
        int renderedHeight =
                Math.round(
                        GUI_HEIGHT
                                * GUI_SCALE
                );

        return (
                height
                        - renderedHeight
        ) / 2;
    }

    /*
     * =========================================================
     * Mouse Coordinate Conversion
     * =========================================================
     *
     * 예:
     *
     * 실제 화면상 192px 크기의 GUI를
     * 다시 내부 256px 좌표로 변환한다.
     */

    private double screenToGuiX(
            double screenX
    ) {
        return (
                screenX
                        - getGuiLeft()
        ) / GUI_SCALE;
    }

    private double screenToGuiY(
            double screenY
    ) {
        return (
                screenY
                        - getGuiTop()
        ) / GUI_SCALE;
    }

    /*
     * =========================================================
     * Hit Test
     * =========================================================
     */

    private static boolean isInside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }
}