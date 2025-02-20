// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.app.appmenu;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import org.chromium.base.CallbackController;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.supplier.ObservableSupplier;
import org.chromium.base.supplier.OneshotSupplier;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.banners.AppMenuVerbiage;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge;
import org.chromium.chrome.browser.bookmarks.BookmarkFeatures;
import org.chromium.chrome.browser.bookmarks.PowerBookmarkUtils;
import org.chromium.chrome.browser.bookmarks.ReadingListFeatures;
import org.chromium.chrome.browser.commerce.ShoppingFeatures;
import org.chromium.chrome.browser.commerce.ShoppingServiceFactory;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.device.DeviceConditions;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.image_descriptions.ImageDescriptionsController;
import org.chromium.chrome.browser.incognito.IncognitoUtils;
import org.chromium.chrome.browser.incognito.reauth.IncognitoReauthController;
import org.chromium.chrome.browser.layouts.LayoutStateProvider;
import org.chromium.chrome.browser.layouts.LayoutType;
import org.chromium.chrome.browser.multiwindow.MultiWindowModeStateDispatcher;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.night_mode.WebContentsDarkModeController;
import org.chromium.chrome.browser.omaha.UpdateMenuItemHelper;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.price_tracking.PriceTrackingUtilities;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.read_later.ReadingListUtils;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.share.ShareUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tasks.ReturnToChromeUtil;
import org.chromium.chrome.browser.tasks.tab_management.TabUiFeatureUtilities;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.translate.TranslateUtils;
import org.chromium.chrome.browser.ui.appmenu.AppMenuHandler;
import org.chromium.chrome.browser.ui.appmenu.AppMenuHandler.AppMenuItemType;
import org.chromium.chrome.browser.ui.appmenu.AppMenuItemProperties;
import org.chromium.chrome.browser.ui.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.ui.appmenu.AppMenuUtil;
import org.chromium.chrome.browser.ui.appmenu.CustomViewBinder;
import org.chromium.chrome.features.start_surface.StartSurface;
import org.chromium.chrome.features.start_surface.StartSurfaceState;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.browser_ui.accessibility.PageZoomCoordinator;
import org.chromium.components.commerce.core.ShoppingService;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.components.embedder_support.util.UrlConstants;
import org.chromium.components.embedder_support.util.UrlUtilities;
import org.chromium.components.power_bookmarks.PowerBookmarkMeta;
import org.chromium.components.webapk.lib.client.WebApkValidator;
import org.chromium.components.webapps.AppBannerManager;
import org.chromium.components.webapps.WebappsUtils;
import org.chromium.content_public.browser.ContentFeatureList;
import org.chromium.net.ConnectionType;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.modelutil.MVCListAdapter;
import org.chromium.ui.modelutil.MVCListAdapter.ModelList;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.url.GURL;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.Hashtable;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Base64InputStream;
import androidx.appcompat.view.menu.MenuBuilder;
import org.chromium.chrome.browser.AppMenuBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content_public.browser.WebContents;

import org.chromium.components.browser_ui.site_settings.SiteSettingsCategory;
import org.chromium.components.browser_ui.site_settings.WebsitePreferenceBridge;
import org.chromium.components.browser_ui.site_settings.WebsitePreferenceBridgeJni;
import org.chromium.components.content_settings.ContentSettingsType;
import org.chromium.components.content_settings.ContentSettingValues;

import org.chromium.chrome.browser.AppMenuBridge;
import org.chromium.base.Log;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import org.chromium.components.browser_ui.site_settings.ContentSettingsResources;

/**
 * Base implementation of {@link AppMenuPropertiesDelegate} that handles hiding and showing menu
 * items based on activity state.
 */
public class AppMenuPropertiesDelegateImpl implements AppMenuPropertiesDelegate {
    private static Boolean sItemBookmarkedForTesting;
    private static Boolean sItemInReadingListForTesting;
    protected PropertyModel mReloadPropertyModel;

    protected final Context mContext;
    protected final boolean mIsTablet;
    protected final ActivityTabProvider mActivityTabProvider;
    protected final MultiWindowModeStateDispatcher mMultiWindowModeStateDispatcher;
    protected final TabModelSelector mTabModelSelector;
    protected final ToolbarManager mToolbarManager;
    protected final View mDecorView;

    private CallbackController mIncognitoReauthCallbackController = new CallbackController();
    private CallbackController mCallbackController = new CallbackController();
    private ObservableSupplier<BookmarkBridge> mBookmarkBridgeSupplier;
    private boolean mUpdateMenuItemVisible;
    private ShareUtils mShareUtils;
    // Keeps track of which menu item was shown when installable app is detected.
    private int mAddAppTitleShown;
    private Map<CustomViewBinder, Integer> mCustomViewTypeOffsetMap;
    private boolean mIsTypeSpecificBookmarkItemRowPresent;
    /**
     * This is non null for the case of ChromeTabbedActivity when the corresponding {@link
     * CallbackController} has been fired.
     */
    private @Nullable IncognitoReauthController mIncognitoReauthController;

    @VisibleForTesting
    @IntDef({MenuGroup.INVALID, MenuGroup.PAGE_MENU, MenuGroup.OVERVIEW_MODE_MENU,
            MenuGroup.TABLET_EMPTY_MODE_MENU})
    @interface MenuGroup {
        int INVALID = -1;
        int PAGE_MENU = 0;
        int OVERVIEW_MODE_MENU = 1;
        int TABLET_EMPTY_MODE_MENU = 2;
    }

    // Please treat this list as append only and keep it in sync with
    // AppMenuHighlightItem in enums.xml.
    @IntDef({AppMenuHighlightItem.UNKNOWN, AppMenuHighlightItem.DOWNLOADS,
            AppMenuHighlightItem.BOOKMARKS, AppMenuHighlightItem.TRANSLATE,
            AppMenuHighlightItem.ADD_TO_HOMESCREEN, AppMenuHighlightItem.DOWNLOAD_THIS_PAGE,
            AppMenuHighlightItem.BOOKMARK_THIS_PAGE, AppMenuHighlightItem.DATA_REDUCTION_FOOTER})
    @Retention(RetentionPolicy.SOURCE)
    @interface AppMenuHighlightItem {
        int UNKNOWN = 0;
        int DOWNLOADS = 1;
        int BOOKMARKS = 2;
        int TRANSLATE = 3;
        int ADD_TO_HOMESCREEN = 4;
        int DOWNLOAD_THIS_PAGE = 5;
        int BOOKMARK_THIS_PAGE = 6;
        int DATA_REDUCTION_FOOTER = 7;
        int NUM_ENTRIES = 8;
    }

    protected @Nullable LayoutStateProvider mLayoutStateProvider;
    private @Nullable OneshotSupplier<StartSurface> mStartSurfaceSupplier;
    private @Nullable StartSurface.StateObserver mStartSurfaceStateObserver;
    private @StartSurfaceState int mStartSurfaceState;
    protected Runnable mAppMenuInvalidator;

    /**
     * Construct a new {@link AppMenuPropertiesDelegateImpl}.
     * @param context The activity context.
     * @param activityTabProvider The {@link ActivityTabProvider} for the containing activity.
     * @param multiWindowModeStateDispatcher The {@link MultiWindowModeStateDispatcher} for the
     *         containing activity.
     * @param tabModelSelector The {@link TabModelSelector} for the containing activity.
     * @param toolbarManager The {@link ToolbarManager} for the containing activity.
     * @param decorView The decor {@link View}, e.g. from Window#getDecorView(), for the containing
     *         activity.
     * @param layoutStateProvidersSupplier An {@link ObservableSupplier} for the
     *         {@link LayoutStateProvider} associated with the containing activity.
     * @param startSurfaceSupplier An {@link OneshotSupplier} for the Start surface.
     * @param bookmarkBridgeSupplier An {@link ObservableSupplier} for the {@link BookmarkBridge}
     * @param incognitoReauthControllerOneshotSupplier An {@link OneshotSupplier} for the {@link
     *         IncognitoReauthController} which is not null for tabbed Activity.
     */
    public AppMenuPropertiesDelegateImpl(Context context, ActivityTabProvider activityTabProvider,
            MultiWindowModeStateDispatcher multiWindowModeStateDispatcher,
            TabModelSelector tabModelSelector, ToolbarManager toolbarManager, View decorView,
            @Nullable OneshotSupplier<LayoutStateProvider> layoutStateProvidersSupplier,
            @Nullable OneshotSupplier<StartSurface> startSurfaceSupplier,
            ObservableSupplier<BookmarkBridge> bookmarkBridgeSupplier,
            @Nullable OneshotSupplier<IncognitoReauthController>
                    incognitoReauthControllerOneshotSupplier) {
        mContext = context;
        mIsTablet = DeviceFormFactor.isNonMultiDisplayContextOnTablet(mContext);
        mActivityTabProvider = activityTabProvider;
        mMultiWindowModeStateDispatcher = multiWindowModeStateDispatcher;
        mTabModelSelector = tabModelSelector;
        mToolbarManager = toolbarManager;
        mDecorView = decorView;

        if (incognitoReauthControllerOneshotSupplier != null) {
            incognitoReauthControllerOneshotSupplier.onAvailable(
                    mIncognitoReauthCallbackController.makeCancelable(incognitoReauthController -> {
                        mIncognitoReauthController = incognitoReauthController;
                    }));
        }

        if (layoutStateProvidersSupplier != null) {
            layoutStateProvidersSupplier.onAvailable(mCallbackController.makeCancelable(
                    layoutStateProvider -> { mLayoutStateProvider = layoutStateProvider; }));
        }

        if (!ReturnToChromeUtil.isTabSwitcherOnlyRefactorEnabled(mContext)
                && startSurfaceSupplier != null) {
            mStartSurfaceSupplier = startSurfaceSupplier;
            startSurfaceSupplier.onAvailable(mCallbackController.makeCancelable((startSurface) -> {
                mStartSurfaceState = startSurface.getStartSurfaceState();
                mStartSurfaceStateObserver = (newState, shouldShowToolbar) -> {
                    assert ReturnToChromeUtil.isStartSurfaceEnabled(mContext);
                    mStartSurfaceState = newState;
                };
                // TODO(https://crbug.com/1315679): Remove |mStartSurfaceSupplier|,
                // |mStartSurfaceState| and |mStartSurfaceStateObserver| after the refactor is
                // enabled by default.
                startSurface.addStateChangeObserver(mStartSurfaceStateObserver);
            }));
        }
        mBookmarkBridgeSupplier = bookmarkBridgeSupplier;
        mShareUtils = new ShareUtils();
    }

    @Override
    public void destroy() {
        if (mCallbackController != null) {
            mCallbackController.destroy();
            mCallbackController = null;
        }
        if (mStartSurfaceSupplier != null) {
            if (mStartSurfaceSupplier.get() != null) {
                mStartSurfaceSupplier.get().removeStateChangeObserver(mStartSurfaceStateObserver);
            }
            mStartSurfaceSupplier = null;
            mStartSurfaceStateObserver = null;
        }
    }

    /**
     * @return The resource id for the menu to use in {@link AppMenu}.
     */
    protected int getAppMenuLayoutId() {
        return R.menu.main_menu;
    }

    @Override
    public @Nullable List<CustomViewBinder> getCustomViewBinders() {
        List<CustomViewBinder> customViewBinders = new ArrayList<>();
        customViewBinders.add(new UpdateMenuItemViewBinder());
        customViewBinders.add(new IncognitoMenuItemViewBinder());
        customViewBinders.add(new DividerLineMenuItemViewBinder());
        return customViewBinders;
    }

    /**
     * @return Whether the app menu for a web page should be shown.
     */
    protected boolean shouldShowPageMenu() {
        boolean isInTabSwitcher = isInTabSwitcher();
        if (mIsTablet) {
            boolean hasTabs = mTabModelSelector.getCurrentModel().getCount() != 0;
            return hasTabs && !isInTabSwitcher;
        } else {
            return !isInTabSwitcher;
        }
    }

    @VisibleForTesting
    @MenuGroup
    int getMenuGroup() {
        // Determine which menu to show.
        @MenuGroup
        int menuGroup = MenuGroup.INVALID;
        if (shouldShowPageMenu()) menuGroup = MenuGroup.PAGE_MENU;

        boolean isInTabSwitcher = isInTabSwitcher();
        if (mIsTablet) {
            boolean hasTabs = mTabModelSelector.getCurrentModel().getCount() != 0;
            if (hasTabs && isInTabSwitcher) {
                menuGroup = MenuGroup.OVERVIEW_MODE_MENU;
            } else if (!hasTabs) {
                menuGroup = MenuGroup.TABLET_EMPTY_MODE_MENU;
            }
        } else if (isInTabSwitcher) {
            menuGroup = MenuGroup.OVERVIEW_MODE_MENU;
        }
        assert menuGroup != MenuGroup.INVALID;
        return menuGroup;
    }

    /**
     * @return Whether the grid tab switcher is showing.
     */
    private boolean isInTabSwitcher() {
        return mLayoutStateProvider != null
                && mLayoutStateProvider.isLayoutVisible(LayoutType.TAB_SWITCHER)
                && !mLayoutStateProvider.isLayoutStartingToHide(LayoutType.TAB_SWITCHER)
                && !isInStartSurfaceHomepage();
    }

    /**
     * @return Whether the Start surface homepage is showing.
     */
    @VisibleForTesting
    boolean isInStartSurfaceHomepage() {
        if (ReturnToChromeUtil.isTabSwitcherOnlyRefactorEnabled(mContext)) {
            return mLayoutStateProvider != null
                    && mLayoutStateProvider.isLayoutVisible(LayoutType.START_SURFACE);
        }

        return mStartSurfaceSupplier != null && mStartSurfaceSupplier.get() != null
                && mStartSurfaceState == StartSurfaceState.SHOWN_HOMEPAGE;
    }

    private void setMenuGroupVisibility(@MenuGroup int menuGroup, Menu menu) {
        menu.setGroupVisible(R.id.PAGE_MENU, menuGroup == MenuGroup.PAGE_MENU);
        menu.setGroupVisible(R.id.OVERVIEW_MODE_MENU, menuGroup == MenuGroup.OVERVIEW_MODE_MENU);
        menu.setGroupVisible(
                R.id.TABLET_EMPTY_MODE_MENU, menuGroup == MenuGroup.TABLET_EMPTY_MODE_MENU);
    }

    @Override
    public ModelList getMenuItems(
            CustomItemViewTypeProvider customItemViewTypeProvider, AppMenuHandler handler) {
        ModelList modelList = new ModelList();

        PopupMenu popup = new PopupMenu(mContext, mDecorView);
        Menu menu = popup.getMenu();
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(getAppMenuLayoutId(), menu);

        prepareMenu(menu, handler);

        Tab currentTab = mActivityTabProvider.get();
        WebContents webContents = null;
        webContents = currentTab != null ? currentTab.getWebContents() : null;

        Menu extensionMenu = new PopupMenu(mContext, mDecorView).getMenu();
        // If we are not showing extensions first, we append the extensions to the existing menu
        if (!ContextUtils.getAppSharedPreferences().getBoolean("show_extensions_first", false))
            extensionMenu = menu;
        prepareExtensionMenu(
                    extensionMenu, isInStartSurfaceHomepage() ? null : currentTab, handler, mTabModelSelector.getCurrentModel().isIncognito());

        boolean menuHasFiveIconsRow = false;
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;

            if (item.getItemId() == R.id.icon_row_menu_id
                && item.getSubMenu().size() == 5)
                menuHasFiveIconsRow = true;
        }

        boolean extensionsHaveBeenAdded = false;

        // TODO(crbug.com/1119550): Programmatically create menu item's PropertyModel instead of
        // converting from MenuItems.
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;

            // If we do not have a five icons row in the main menu, we immediately show the extensions
            if (ContextUtils.getAppSharedPreferences().getBoolean("show_extensions_first", false)
                && !menuHasFiveIconsRow
                && !extensionsHaveBeenAdded) {
                    extensionsHaveBeenAdded = true;
                    for (int j = 0; j < extensionMenu.size(); ++j) {
                        MenuItem extensionItem = extensionMenu.getItem(j);
                        if (!extensionItem.isVisible()) continue;
                        PropertyModel extensionPropertyModel = AppMenuUtil.menuItemToPropertyModel(extensionItem);
                        extensionPropertyModel.set(AppMenuItemProperties.ICON_COLOR_RES, getMenuItemIconColorRes(extensionItem));
                        extensionPropertyModel.set(AppMenuItemProperties.SUPPORT_ENTER_ANIMATION, true);

                        modelList.add(new MVCListAdapter.ListItem(AppMenuItemType.STANDARD, extensionPropertyModel));
                    }
            }

            PropertyModel propertyModel = AppMenuUtil.menuItemToPropertyModel(item);
            propertyModel.set(AppMenuItemProperties.ICON_COLOR_RES, getMenuItemIconColorRes(item));
            propertyModel.set(AppMenuItemProperties.SUPPORT_ENTER_ANIMATION, true);
            propertyModel.set(AppMenuItemProperties.MENU_ICON_AT_START, isMenuIconAtStart());
            if (item.hasSubMenu()) {
                // Only support top level menu items have SUBMENU, and a SUBMENU item cannot have a
                // SUBMENU.
                // TODO(crbug.com/1183234) : Create a new SubMenuItemProperties property key set for
                // SUBMENU items.
                ModelList subList = new ModelList();
                for (int j = 0; j < item.getSubMenu().size(); ++j) {
                    MenuItem subitem = item.getSubMenu().getItem(j);
                    if (!subitem.isVisible()) continue;

                    PropertyModel subModel = AppMenuUtil.menuItemToPropertyModel(subitem);
                    subList.add(new MVCListAdapter.ListItem(0, subModel));
                    if (subitem.getItemId() == R.id.reload_menu_id) {
                        mReloadPropertyModel = subModel;
                        loadingStateChanged(currentTab == null ? false : currentTab.isLoading());
                    }
                }
                propertyModel.set(AppMenuItemProperties.SUBMENU, subList);
            }
            int menutype = AppMenuItemType.STANDARD;
            if (item.getItemId() == R.id.request_desktop_site_row_menu_id
                    || item.getItemId() == R.id.share_row_menu_id
                    || item.getItemId() == R.id.auto_dark_web_contents_row_menu_id
                    || item.getItemId() == R.id.adblock_row_menu_id) {
                menutype = AppMenuItemType.TITLE_BUTTON;
            } else if (item.getItemId() == R.id.icon_row_menu_id) {
                int viewCount = item.getSubMenu().size();
                if (viewCount == 3) {
                    menutype = AppMenuItemType.THREE_BUTTON_ROW;
                } else if (viewCount == 4) {
                    menutype = AppMenuItemType.FOUR_BUTTON_ROW;
                } else if (viewCount == 5) {
                    menutype = AppMenuItemType.FIVE_BUTTON_ROW;
                }
            } else {
                // Could be standard items or custom items.
                int customType = customItemViewTypeProvider.fromMenuItemId(item.getItemId());
                if (customType != CustomViewBinder.NOT_HANDLED) {
                    menutype = customType;
                }
            }
            modelList.add(new MVCListAdapter.ListItem(menutype, propertyModel));

            // If we chose to show extensions first, we append them after the first row that has 5 action buttons
            if (ContextUtils.getAppSharedPreferences().getBoolean("show_extensions_first", false)
                && !extensionsHaveBeenAdded
                && menuHasFiveIconsRow
                // current row is five icons row
                && item.getItemId() == R.id.icon_row_menu_id
                && item.getSubMenu().size() == 5) {
                    extensionsHaveBeenAdded = true;
                    for (int j = 0; j < extensionMenu.size(); ++j) {
                        MenuItem extensionItem = extensionMenu.getItem(j);
                        if (!extensionItem.isVisible()) continue;
                        PropertyModel extensionPropertyModel = AppMenuUtil.menuItemToPropertyModel(extensionItem);
                        extensionPropertyModel.set(AppMenuItemProperties.ICON_COLOR_RES, getMenuItemIconColorRes(extensionItem));
                        extensionPropertyModel.set(AppMenuItemProperties.SUPPORT_ENTER_ANIMATION, true);

                        modelList.add(new MVCListAdapter.ListItem(AppMenuItemType.STANDARD, extensionPropertyModel));
                    }
            }
        }

        return modelList;
    }

    @Override
    public void prepareMenu(Menu menu, AppMenuHandler handler) {
        int menuGroup = getMenuGroup();
        setMenuGroupVisibility(menuGroup, menu);

        boolean isIncognito = mTabModelSelector.getCurrentModel().isIncognito();
        Tab currentTab = mActivityTabProvider.get();

        if (menuGroup == MenuGroup.PAGE_MENU) {
            preparePageMenu(
                    menu, isInStartSurfaceHomepage() ? null : currentTab, handler, isIncognito);
        }
        prepareCommonMenuItems(menu, menuGroup, isIncognito);
    }

    private void prepareExtensionMenu(
            Menu menu, @Nullable Tab currentTab, AppMenuHandler handler, boolean isIncognito) {

        WebContents webContents = null;
        webContents = currentTab != null ? currentTab.getWebContents() : null;

        boolean canShowExtensions = false;
        if (currentTab != null)
          canShowExtensions = true;

        int numItems = menu.size();

        if (canShowExtensions) {
          int itemIndex = numItems++;
          String extensions = "";
          if (isIncognito)
            extensions = AppMenuBridge.getRunningExtensions(Profile.fromWebContents(webContents).getPrimaryOTRProfile(true), webContents);
          else
            extensions = AppMenuBridge.getRunningExtensions(Profile.fromWebContents(webContents).getOriginalProfile(), webContents);
          if (!extensions.isEmpty()) {
            String[] extensionsArray = extensions.split("\u001f");
            for (String extension: extensionsArray) {
              String[] extensionsInfo = extension.split("\u001e");
              MenuItem newlyAdded = menu.add(999999, 999999 + itemIndex, Menu.NONE, extensionsInfo[0]);
              if (extensionsInfo.length > 1) {
                newlyAdded.setTitleCondensed("Extension: " + extensionsInfo[1]);
              }

              if (extensionsInfo.length > 2 && !extensionsInfo[2].equals("")) {
                newlyAdded.setTitleCondensed("Extension: " + extensionsInfo[1] + ": " + extensionsInfo[2]);
              }

              if (extensionsInfo.length > 3) {
                String cleanImage = extensionsInfo[3].replace("data:image/png;base64,", "").replace("data:image/jpeg;base64,","").replace("data:image/gif;base64,", "");
                byte[] decodedString = Base64.decode(cleanImage, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                newlyAdded.setIcon(new BitmapDrawable(mContext.getResources(), decodedByte));

                boolean isIncognitoEnabled = false;
                if (extensionsInfo[4].equals("active"))
                  isIncognitoEnabled = true;
                if (!isIncognitoEnabled && isIncognito) {
                  SpannableString spanString = new SpannableString(newlyAdded.getTitle().toString());
                  spanString.setSpan(new ForegroundColorSpan(Color.GRAY), 0, spanString.length(), 0);
                  newlyAdded.setTitle(spanString);
                  newlyAdded.setTitleCondensed("Extension (inactive): " + extensionsInfo[1] + ": " + extensionsInfo[2]);
                  newlyAdded.setIcon(ContentSettingsResources.getBlockedSquareIcon(mContext.getResources(), newlyAdded.getIcon()));
                }
              }
              itemIndex++;
           }
         }
       }
    }

    /**
     * Prepare the menu items. Note: it is possible that currentTab is null.
     */
    private void preparePageMenu(
            Menu menu, @Nullable Tab currentTab, AppMenuHandler handler, boolean isIncognito) {
        // Multiple menu items shouldn't be enabled when the currentTab is null. Use a flag to
        // indicate whether the current Tab isn't null.
        boolean isCurrentTabNotNull = currentTab != null;

        GURL url = isCurrentTabNotNull ? currentTab.getUrl() : GURL.emptyGURL();
        final boolean isChromeScheme = url.getScheme().equals(UrlConstants.CHROME_SCHEME)
                || url.getScheme().equals(UrlConstants.CHROME_NATIVE_SCHEME);
        final boolean isFileScheme = url.getScheme().equals(UrlConstants.FILE_SCHEME);
        final boolean isContentScheme = url.getScheme().equals(UrlConstants.CONTENT_SCHEME);
        final boolean isHttpOrHttpsScheme = UrlUtilities.isHttpOrHttps(url);

        // Update the icon row items (shown in narrow form factors).
        boolean shouldShowIconRow = shouldShowIconRow();
        if (menu.findItem(R.id.icon_row_menu_id) != null)
        menu.findItem(R.id.icon_row_menu_id).setVisible(shouldShowIconRow);
        if (shouldShowIconRow) {
            SubMenu actionBar = menu.findItem(R.id.icon_row_menu_id).getSubMenu();

            // Disable the "Forward" menu item if there is no page to go to.
            MenuItem forwardMenuItem = actionBar.findItem(R.id.forward_menu_id);
            forwardMenuItem.setEnabled(isCurrentTabNotNull && currentTab.canGoForward());

            Drawable icon = AppCompatResources.getDrawable(mContext, R.drawable.btn_reload_stop);
            DrawableCompat.setTintList(icon,
                    AppCompatResources.getColorStateList(
                            mContext, R.color.default_icon_color_tint_list));
            actionBar.findItem(R.id.reload_menu_id).setIcon(icon);
            loadingStateChanged(isCurrentTabNotNull && currentTab.isLoading());

            MenuItem bookmarkMenuItemShortcut = actionBar.findItem(R.id.bookmark_this_page_id);
            updateBookmarkMenuItemShortcut(bookmarkMenuItemShortcut, currentTab, /*fromCCT=*/false);

            MenuItem offlineMenuItem = actionBar.findItem(R.id.offline_page_id);
            offlineMenuItem.setEnabled(isCurrentTabNotNull && shouldEnableDownloadPage(currentTab));

            if (!isCurrentTabNotNull) {
                actionBar.findItem(R.id.info_menu_id).setEnabled(false);
                actionBar.findItem(R.id.reload_menu_id).setEnabled(false);
            }
            assert actionBar.size() == 5;
        }

        mUpdateMenuItemVisible = shouldShowUpdateMenuItem();
        if (menu.findItem(R.id.update_menu_id) != null)
        menu.findItem(R.id.update_menu_id).setVisible(mUpdateMenuItemVisible);
        if (mUpdateMenuItemVisible) {
            mAppMenuInvalidator = () -> handler.invalidateAppMenu();
            UpdateMenuItemHelper.getInstance().registerObserver(mAppMenuInvalidator);
        }

        if (menu.findItem(R.id.new_window_menu_id) != null)
        menu.findItem(R.id.new_window_menu_id).setVisible(shouldShowNewWindow());
        if (menu.findItem(R.id.move_to_other_window_menu_id) != null)
        menu.findItem(R.id.move_to_other_window_menu_id).setVisible(shouldShowMoveToOtherWindow());
        MenuItem menu_all_windows = menu.findItem(R.id.manage_all_windows_menu_id);
        boolean showManageAllWindows = shouldShowManageAllWindows();
        if (menu_all_windows != null)
        menu_all_windows.setVisible(showManageAllWindows);
        if (showManageAllWindows) {
            if (menu_all_windows != null)
            menu_all_windows.setTitle(
                    mContext.getString(R.string.menu_manage_all_windows, getInstanceCount()));
        }

        updateBookmarkMenuItemRow(menu.findItem(R.id.add_bookmark_menu_id),
                menu.findItem(R.id.edit_bookmark_menu_id), currentTab);
        // Updates the type-specific bookmark menu item rows.
        // The order listed here is reflects the relative priority. Subsequent updates should check
        // the `mIsTypeSpecificBookmarkItemRowPresent` bit before updating their row.
        mIsTypeSpecificBookmarkItemRowPresent = false;
        updatePriceTrackingMenuItemRow(menu.findItem(R.id.enable_price_tracking_menu_id),
                menu.findItem(R.id.disable_price_tracking_menu_id), currentTab);
        updateReadingListMenuItemRow(menu.findItem(R.id.add_to_reading_list_menu_id),
                menu.findItem(R.id.delete_from_reading_list_menu_id), currentTab);

        // Don't allow either "chrome://" pages or interstitial pages to be shared, or when the
        // current tab is null.
        if (menu.findItem(R.id.share_row_menu_id) != null)
        menu.findItem(R.id.share_row_menu_id)
                .setVisible(isCurrentTabNotNull && mShareUtils.shouldEnableShare(currentTab));

        if (isCurrentTabNotNull) {
            ShareHelper.configureDirectShareMenuItem(
                    mContext, menu.findItem(R.id.direct_share_menu_id));
        }

        if (menu.findItem(R.id.paint_preview_show_id) != null)
        menu.findItem(R.id.paint_preview_show_id)
                .setVisible(isCurrentTabNotNull
                        && shouldShowPaintPreview(isChromeScheme, currentTab, isIncognito));

        // Enable image descriptions if touch exploration is currently enabled.
        if (ImageDescriptionsController.getInstance().shouldShowImageDescriptionsMenuItem()) {
            if (menu.findItem(R.id.get_image_descriptions_id) != null)
            menu.findItem(R.id.get_image_descriptions_id).setVisible(true);

            int titleId = R.string.menu_stop_image_descriptions;
            Profile profile = Profile.getLastUsedRegularProfile();
            // If image descriptions are not enabled, then we want the menu item to be "Get".
            if (!ImageDescriptionsController.getInstance().imageDescriptionsEnabled(profile)) {
                titleId = R.string.menu_get_image_descriptions;
            } else if (ImageDescriptionsController.getInstance().onlyOnWifiEnabled(profile)
                    && DeviceConditions.getCurrentNetConnectionType(mContext)
                            != ConnectionType.CONNECTION_WIFI) {
                // If image descriptions are enabled, then we want "Stop", except in the special
                // case that the user specified only on Wifi, and we are not currently on Wifi.
                titleId = R.string.menu_get_image_descriptions;
            }

            menu.findItem(R.id.get_image_descriptions_id).setTitle(titleId);
        } else {
            if (menu.findItem(R.id.get_image_descriptions_id) != null)
            menu.findItem(R.id.get_image_descriptions_id).setVisible(false);
        }

        // Conditionally add the Zoom menu item.
        if (menu.findItem(R.id.page_zoom_id) != null)
        menu.findItem(R.id.page_zoom_id).setVisible(PageZoomCoordinator.shouldShowMenuItem());

        // Disable find in page on the native NTP or on Start surface.
        if (menu.findItem(R.id.find_in_page_id) != null)
        menu.findItem(R.id.find_in_page_id)
                .setVisible(isCurrentTabNotNull && shouldShowFindInPage(currentTab));

        // Prepare translate menu button.
        prepareTranslateMenuItem(menu, currentTab);

        prepareAddToHomescreenMenuItem(menu, currentTab,
                shouldShowHomeScreenMenuItem(
                        isChromeScheme, isFileScheme, isContentScheme, isIncognito, url));

        updateRequestDesktopSiteMenuItem(menu, currentTab, true /* can show */, isChromeScheme);

        updateAutoDarkMenuItem(menu, currentTab, isChromeScheme);

        updateAdblockMenuItem(menu, currentTab, true /* can show */);
        MenuItem nightModeMenu = menu.findItem(R.id.night_mode_switcher_id);
        if (nightModeMenu != null) {
               if (ContextUtils.getAppSharedPreferences().getBoolean("darken_websites_enabled", false)) {
                   nightModeMenu.setTitle(R.string.main_menu_turn_off_night_mode);
                   nightModeMenu.setIcon(R.drawable.ic_night_mode_off);
               } else {
                   nightModeMenu.setTitle(R.string.main_menu_turn_on_night_mode);
                   nightModeMenu.setIcon(R.drawable.ic_night_mode_on);
               }
        }

        MenuItem disableProxyMenu = menu.findItem(R.id.disable_proxy_id);
        boolean isProxyEnabled = AppMenuBridge.isProxyEnabled(Profile.getLastUsedRegularProfile());
        if (isProxyEnabled) {
            if (disableProxyMenu != null)
                disableProxyMenu.setVisible(true);
        } else {
            if (disableProxyMenu != null)
                disableProxyMenu.setVisible(false);
        }

        // Only display reader mode settings menu option if the current page is in reader mode.
        if (menu.findItem(R.id.reader_mode_prefs_id) != null)
        menu.findItem(R.id.reader_mode_prefs_id)
                .setVisible(isCurrentTabNotNull && shouldShowReaderModePrefs(currentTab));

        // Only display the Enter VR button if VR Shell Dev environment is enabled.
        if (menu.findItem(R.id.enter_vr_id) != null)
        menu.findItem(R.id.enter_vr_id).setVisible(isCurrentTabNotNull && shouldShowEnterVr());

        updateManagedByMenuItem(menu, currentTab);
        if (menu.findItem(R.id.help_id) != null)
            menu.findItem(R.id.help_id).setVisible(false);
    }

    /**
     * @return The number of Chrome instances either running alive or dormant but the state
     *         is present for restoration.
     */
    private int getInstanceCount() {
        return mMultiWindowModeStateDispatcher.getInstanceCount();
    }

    private void prepareCommonMenuItems(Menu menu, @MenuGroup int menuGroup, boolean isIncognito) {
        // We have to iterate all menu items since same menu item ID may be associated with more
        // than one menu items.
        boolean isOverviewModeMenu = menuGroup == MenuGroup.OVERVIEW_MODE_MENU;
        boolean isMenuGroupTabsVisible = isOverviewModeMenu
                && TabUiFeatureUtilities.isTabGroupsAndroidEnabled(mContext)
                && !DeviceClassManager.enableAccessibilityLayout(mContext);
        boolean isMenuGroupTabsEnabled = isMenuGroupTabsVisible
                && mTabModelSelector.getTabModelFilterProvider()
                                .getCurrentTabModelFilter()
                                .getTabsWithNoOtherRelatedTabs()
                                .size()
                        > 1;
        boolean isPriceTrackingVisible = isOverviewModeMenu
                && PriceTrackingUtilities.shouldShowPriceTrackingMenu()
                && !DeviceClassManager.enableAccessibilityLayout(mContext) && !isIncognito;
        boolean isPriceTrackingEnabled = isPriceTrackingVisible;
        boolean hasItemBetweenDividers = false;

        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (!shouldShowIconBeforeItem()) {
                // Remove icons for menu items except the reader mode prefs and the update menu
                // item.
                if (item.getItemId() != R.id.reader_mode_prefs_id
                        && item.getItemId() != R.id.update_menu_id) {
                    item.setIcon(null);
                }

                // Remove title button icons.
                if (item.getItemId() == R.id.request_desktop_site_row_menu_id
                        || item.getItemId() == R.id.share_row_menu_id
                        || item.getItemId() == R.id.auto_dark_web_contents_row_menu_id) {
                    item.getSubMenu().getItem(0).setIcon(null);
                }
            }

            if (item.getItemId() == R.id.new_incognito_tab_menu_id && item.isVisible()) {
                // Disable new incognito tab when a re-authentication might be pending.
                boolean isIncognitoReauthPending = (mIncognitoReauthController != null)
                        && mIncognitoReauthController.isIncognitoReauthPending();

                // Disable new incognito tab when it is blocked (e.g. by a policy).
                // findItem(...).setEnabled(...)" is not enough here, because of the inflated
                // main_menu.xml contains multiple items with the same id in different groups
                // e.g.: menu_new_incognito_tab.
                item.setEnabled(isIncognitoEnabled() && !isIncognitoReauthPending);
            }

            if (item.getItemId() == R.id.divider_line_id) {
                item.setEnabled(false);
            }

            int itemGroupId = item.getGroupId();
            if (!(menuGroup == MenuGroup.OVERVIEW_MODE_MENU
                                && itemGroupId == R.id.OVERVIEW_MODE_MENU
                        || menuGroup == MenuGroup.PAGE_MENU && itemGroupId == R.id.PAGE_MENU)) {
                continue;
            }

            if (item.getItemId() == R.id.recent_tabs_menu_id) {
                item.setVisible(!isIncognito);
            }
            if (item.getItemId() == R.id.menu_group_tabs) {
                // Disable incognito group tabs when a re-authentication screen is shown.
                // We show the re-auth screen only in Incognito mode.
                boolean isIncognitoReauthShowing = isIncognito
                        && (mIncognitoReauthController != null)
                        && mIncognitoReauthController.isReauthPageShowing();

                item.setVisible(isMenuGroupTabsVisible);
                item.setEnabled(!isIncognitoReauthShowing && isMenuGroupTabsEnabled);
            }
            if (item.getItemId() == R.id.track_prices_row_menu_id) {
                item.setVisible(isPriceTrackingVisible);
                item.setEnabled(isPriceTrackingEnabled);
            }
            if (item.getItemId() == R.id.close_all_tabs_menu_id) {
                boolean hasTabs = mTabModelSelector.getTotalTabCount() > 0;
                item.setVisible(!isIncognito && isOverviewModeMenu);
                item.setEnabled(hasTabs);
            }
            if (item.getItemId() == R.id.close_all_incognito_tabs_menu_id) {
                boolean hasIncognitoTabs = mTabModelSelector.getModel(true).getCount() > 0;
                item.setVisible(isIncognito && isOverviewModeMenu);
                item.setEnabled(hasIncognitoTabs);
            }
            // This needs to be done after the visibility of the item is set.
            if (item.getItemId() == R.id.divider_line_id) {
                if (!hasItemBetweenDividers) {
                    // If there isn't any visible menu items between the two divider lines, mark
                    // this line invisible.
                    item.setVisible(false);
                } else {
                    hasItemBetweenDividers = false;
                }
            } else if (!hasItemBetweenDividers && item.isVisible()) {
                // When the item isn't a divider line and is visible, we set hasItemBetweenDividers
                // to be true.
                hasItemBetweenDividers = true;
            }
        }
    }

    protected void updateAdblockMenuItem(
            Menu menu, Tab currentTab, boolean canShowAdblockMenu) {
        MenuItem adblockMenuRow = menu.findItem(R.id.adblock_row_menu_id);
        MenuItem adblockMenuLabel = menu.findItem(R.id.adblock_id);
        MenuItem adblockMenuCheck = menu.findItem(R.id.adblock_check_id);

        if (currentTab == null || currentTab.getUrl() == null) { if (adblockMenuRow != null) adblockMenuRow.setVisible(false); return ; }
        String url = currentTab.getUrl().getSpec();
        boolean isChromeScheme = url.startsWith(UrlConstants.CHROME_URL_PREFIX)
                || url.startsWith(UrlConstants.CHROME_NATIVE_URL_PREFIX);
        // Also hide adblock desktop site on Reader Mode.
        boolean isDistilledPage = DomDistillerUrlUtils.isDistilledPage(url);

        // adsEnabled means "adBlockingEnabled"
        boolean itemVisible = canShowAdblockMenu
                && !isChromeScheme && !currentTab.isNativePage() && !isDistilledPage;
        if (adblockMenuRow != null)
            adblockMenuRow.setVisible(itemVisible);
        if (!itemVisible) return;

        boolean adBlockIsActive = (WebsitePreferenceBridgeJni.get().isContentSettingEnabled(Profile.getLastUsedRegularProfile(), ContentSettingsType.ADS) == false);
        if (!adBlockIsActive) {
            adblockMenuCheck.setChecked(false);
            adblockMenuLabel.setIcon(R.drawable.ic_adblock_off);
        } else {
            int adblockSettingForThisSite = WebsitePreferenceBridgeJni.get().getPermissionSettingForOrigin(Profile.getLastUsedRegularProfile(), ContentSettingsType.ADS, currentTab.getUrl().getSpec(), currentTab.getUrl().getSpec());
            if (adblockSettingForThisSite == ContentSettingValues.DEFAULT || adblockSettingForThisSite == ContentSettingValues.BLOCK){
                adblockMenuCheck.setChecked(true);
                adblockMenuLabel.setIcon(R.drawable.ic_adblock_on);
            }
            else {
                adblockMenuCheck.setChecked(false);
                adblockMenuLabel.setIcon(R.drawable.ic_adblock_off);
            }
       }
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether the reader mode preferences menu item should be displayed.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldShowReaderModePrefs(@NonNull Tab currentTab) {
        return DomDistillerUrlUtils.isDistilledPage(currentTab.getUrl());
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether the {@code currentTab} may be downloaded, indicating whether the download
     *         page menu item should be enabled.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldEnableDownloadPage(@NonNull Tab currentTab) {
        return DownloadUtils.isAllowedToDownloadPage(currentTab);
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether bookmark page menu item should be checked, indicating that the current tab
     *         is bookmarked.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldCheckBookmarkStar(@NonNull Tab currentTab) {
        if (sItemBookmarkedForTesting != null) return sItemBookmarkedForTesting;

        if (!mBookmarkBridgeSupplier.hasValue()) return false;
        return mBookmarkBridgeSupplier.get().hasBookmarkIdForTab(currentTab);
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether reading list menu item should be highlighted, indicating that the current tab
     *         exists in the reading list.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldHighlightReadingList(@NonNull Tab currentTab) {
        if (sItemInReadingListForTesting != null) return sItemInReadingListForTesting;

        if (!mBookmarkBridgeSupplier.hasValue()) return false;
        BookmarkId existingBookmark =
                mBookmarkBridgeSupplier.get().getUserBookmarkIdForTab(currentTab);
        return existingBookmark != null && existingBookmark.getType() == BookmarkType.READING_LIST;
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether price tracking is enabled and the button should be highlighted.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldHighlightPriceTracking(@NonNull Tab currentTab) {
        // TODO(crbug.com/1266624): Read this information from power bookmarks when available.
        return false;
    }

    /**
     * @return Whether the update Chrome menu item should be displayed.
     */
    protected boolean shouldShowUpdateMenuItem() {
        return UpdateMenuItemHelper.getInstance().getUiState().itemState != null;
    }

    /**
     * @return Whether the "Move to other window" menu item should be displayed.
     */
    protected boolean shouldShowMoveToOtherWindow() {
        if (!instanceSwitcherEnabled() && shouldShowNewWindow()) return false;
        boolean hasMoreThanOneTab = mTabModelSelector.getTotalTabCount() > 1;
        boolean showAlsoForSingleTab = !isPartnerHomepageEnabled();
        if (!hasMoreThanOneTab && !showAlsoForSingleTab) return false;
        if (instanceSwitcherEnabled()) {
            // Moving tabs should be possible to any other instance.
            return getInstanceCount() > 1;
        } else {
            return mMultiWindowModeStateDispatcher.isOpenInOtherWindowSupported();
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public boolean instanceSwitcherEnabled() {
        return MultiWindowUtils.instanceSwitcherEnabled()
                && MultiWindowUtils.isMultiInstanceApi31Enabled();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public boolean isTabletSizeScreen() {
        return mIsTablet;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public boolean isPartnerHomepageEnabled() {
        return PartnerBrowserCustomizations.getInstance().isHomepageProviderAvailableAndEnabled();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public boolean isNewWindowMenuFeatureEnabled() {
        return ChromeFeatureList.sNewWindowAppMenu.isEnabled();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public boolean isAutoDarkWebContentsEnabled() {
        Profile profile = mTabModelSelector.getCurrentModel().getProfile();
        assert profile != null;
        boolean isFlagEnabled = ChromeFeatureList.isEnabled(
                ChromeFeatureList.DARKEN_WEBSITES_CHECKBOX_IN_THEMES_SETTING);
        boolean isFeatureEnabled =
                WebContentsDarkModeController.isFeatureEnabled(mContext, profile);
        return isFlagEnabled && isFeatureEnabled;
    }

    /**
     * @return Whether the "New window" menu item should be displayed.
     */
    protected boolean shouldShowNewWindow() {
        if (!isNewWindowMenuFeatureEnabled()) return false;
        if (instanceSwitcherEnabled()) {
            // Hide the menu if we already have the maximum number of windows.
            if (getInstanceCount() >= MultiWindowUtils.getMaxInstances()) return false;

            // On phones, show the menu only when in split-screen, with a single instance
            // running on the foreground.
            return isTabletSizeScreen()
                    || (!mMultiWindowModeStateDispatcher.isChromeRunningInAdjacentWindow()
                            && (mMultiWindowModeStateDispatcher.isInMultiWindowMode()
                                    || mMultiWindowModeStateDispatcher.isInMultiDisplayMode()));
        } else {
            if (mMultiWindowModeStateDispatcher.isMultiInstanceRunning()) return false;
            return (mMultiWindowModeStateDispatcher.canEnterMultiWindowMode()
                           && isTabletSizeScreen())
                    || mMultiWindowModeStateDispatcher.isInMultiWindowMode()
                    || mMultiWindowModeStateDispatcher.isInMultiDisplayMode();
        }
    }

    private boolean shouldShowManageAllWindows() {
        return MultiWindowUtils.shouldShowManageWindowsMenu();
    }

    /**
     * @param isChromeScheme Whether URL for the current tab starts with the chrome:// scheme.
     * @param currentTab The currentTab for which the app menu is showing.
     * @param isIncognito Whether the currentTab is incognito.
     * @return Whether the paint preview menu item should be displayed.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldShowPaintPreview(
            boolean isChromeScheme, @NonNull Tab currentTab, boolean isIncognito) {
        return ChromeFeatureList.sPaintPreviewDemo.isEnabled() && !isChromeScheme && !isIncognito;
    }

    /**
     * @param currentTab The currentTab for which the app menu is showing.
     * @return Whether the find in page menu item should be displayed.
     */
    protected boolean shouldShowFindInPage(@NonNull Tab currentTab) {
        return !currentTab.isNativePage() && currentTab.getWebContents() != null;
    }

    /**
     * @return Whether the enter VR menu item should be displayed.
     */
    protected boolean shouldShowEnterVr() {
        return CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VR_SHELL_DEV);
    }

    /**
     * This method should only be called once per context menu shown.
     * @param currentTab The currentTab for which the app menu is showing.
     * @param logging Whether logging should be performed in this check.
     * @return Whether the translate menu item should be displayed.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public boolean shouldShowTranslateMenuItem(@NonNull Tab currentTab) {
        return TranslateUtils.canTranslateCurrentTab(currentTab, true);
    }

    /**
     * @param isChromeScheme Whether URL for the current tab starts with the chrome:// scheme.
     * @param isFileScheme Whether URL for the current tab starts with the file:// scheme.
     * @param isContentScheme Whether URL for the current tab starts with the file:// scheme.
     * @param isIncognito Whether the current tab is incognito.
     * @param url The URL for the current tab.
     * @return Whether the homescreen menu item should be displayed.
     */
    protected boolean shouldShowHomeScreenMenuItem(boolean isChromeScheme, boolean isFileScheme,
            boolean isContentScheme, boolean isIncognito, @NonNull GURL url) {
        // Hide 'Add to homescreen' for the following:
        // * chrome:// pages - Android doesn't know how to direct those URLs.
        // * incognito pages - To avoid problems where users create shortcuts in incognito
        //                      mode and then open the webapp in regular mode.
        // * file:// - After API 24, file: URIs are not supported in VIEW intents and thus
        //             can not be added to the homescreen.
        // * content:// - Accessing external content URIs requires the calling app to grant
        //                access to the resource via FLAG_GRANT_READ_URI_PERMISSION, and that
        //                is not persisted when adding to the homescreen.
        // * If creating shortcuts it not supported by the current home screen.
        return WebappsUtils.isAddToHomeIntentSupported() && !isChromeScheme && !isFileScheme
                && !isContentScheme && !isIncognito && !url.isEmpty();
    }

    /**
     * @param currentTab Current tab being displayed.
     * @return Whether the "Managed by your organization" menu item should be displayed.
     */
    protected boolean shouldShowManagedByMenuItem(Tab currentTab) {
        return false;
    }

    /**
     * Sets the visibility and labels of the "Add to Home screen" and "Open WebAPK" menu items.
     */
    protected void prepareAddToHomescreenMenuItem(
            Menu menu, Tab currentTab, boolean shouldShowHomeScreenMenuItem) {
        MenuItem homescreenItem = menu.findItem(R.id.add_to_homescreen_id);
        MenuItem openWebApkItem = menu.findItem(R.id.open_webapk_id);
        if (homescreenItem == null || openWebApkItem == null) return ;
        mAddAppTitleShown = AppMenuVerbiage.APP_MENU_OPTION_UNKNOWN;
        if (currentTab != null && shouldShowHomeScreenMenuItem) {
            Context context = ContextUtils.getApplicationContext();
            long addToHomeScreenStart = SystemClock.elapsedRealtime();
            ResolveInfo resolveInfo = WebApkValidator.queryFirstWebApkResolveInfo(
                    context, currentTab.getUrl().getSpec());
            RecordHistogram.recordTimesHistogram("Android.PrepareMenu.OpenWebApkVisibilityCheck",
                    SystemClock.elapsedRealtime() - addToHomeScreenStart);

            boolean openWebApkItemVisible =
                    resolveInfo != null && resolveInfo.activityInfo.packageName != null;

            if (openWebApkItemVisible) {
                String appName = resolveInfo.loadLabel(context.getPackageManager()).toString();
                openWebApkItem.setTitle(context.getString(R.string.menu_open_webapk, appName));

                homescreenItem.setVisible(false);
                openWebApkItem.setVisible(true);
            } else {
                AppBannerManager.InstallStringPair installStrings =
                        getAddToHomeScreenTitle(currentTab);
                homescreenItem.setTitle(installStrings.titleTextId);
                homescreenItem.setVisible(true);
                openWebApkItem.setVisible(false);

                if (installStrings.titleTextId == AppBannerManager.NON_PWA_PAIR.titleTextId) {
                    mAddAppTitleShown = AppMenuVerbiage.APP_MENU_OPTION_ADD_TO_HOMESCREEN;
                } else if (installStrings.titleTextId == AppBannerManager.PWA_PAIR.titleTextId) {
                    mAddAppTitleShown = AppMenuVerbiage.APP_MENU_OPTION_INSTALL;
                }
            }
        } else {
            homescreenItem.setVisible(false);
            openWebApkItem.setVisible(false);
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public AppBannerManager.InstallStringPair getAddToHomeScreenTitle(@NonNull Tab currentTab) {
        return AppBannerManager.getHomescreenLanguageOption(currentTab.getWebContents());
    }

    @Override
    public Bundle getBundleForMenuItem(int itemId) {
        Bundle bundle = new Bundle();
        if (itemId == R.id.add_to_homescreen_id) {
            bundle.putInt(AppBannerManager.MENU_TITLE_KEY, mAddAppTitleShown);
        }
        return bundle;
    }

    /**
     * Sets the visibility of the "Translate" menu item.
     */
    protected void prepareTranslateMenuItem(Menu menu, @Nullable Tab currentTab) {
        boolean isTranslateVisible = currentTab != null && shouldShowTranslateMenuItem(currentTab);
        if (menu.findItem(R.id.translate_id) != null)
        menu.findItem(R.id.translate_id).setVisible(isTranslateVisible);
        if (currentTab == null || currentTab.getUrl() == null) return ;
        String url = currentTab.getUrl().getSpec();
            MenuItem translate_menu = menu.findItem(R.id.translate_id);
            if (translate_menu != null) {
                   try {
                       if (url != null
                        &&
                          (
                            url.contains("www.microsofttranslator.com/bv.aspx")
                        ||  url.contains("translatetheweb.com")
                        ||  url.contains("translatetheweb.net")
                        ||  url.contains("translatetheweb-int.net")
                        ||  url.contains("translatoruser.com")
                        ||  url.contains("translatoruser.net")
                          )
                        ) {
                           translate_menu.setTitle(R.string.main_menu_translate_undo);
                       } else {
                           translate_menu.setTitle(R.string.menu_translate);
                       }
                       if (url != null
                        &&
                          (
                            url.startsWith("https://translate.google.com/")
                        ||  url.startsWith("https://translate.googleusercontent.com/")
                        ||  url.startsWith("http://translate.google.com/")
                        ||  url.startsWith("http://translate.googleusercontent.com/")
                        ||  url.contains(".translate.goog/")
                          )
                        ) {
                           translate_menu.setTitle(R.string.main_menu_translate_undo);
                       }
                       if (url != null
                        &&
                          (
                            url.startsWith("https://fanyi.baidu.com/")
                        ||  url.startsWith("http://fanyi.baidu.com/")
                          )
                        ) {
                           translate_menu.setTitle(R.string.main_menu_translate_undo);
                       }
                       if (url != null
                        &&
                          (
                            url.startsWith("https://translate.yandex.com/")
                        ||  url.startsWith("http://translate.yandex.com/")
                          )
                        ) {
                           translate_menu.setTitle(R.string.main_menu_translate_undo);
                       }
                   } catch (Exception e) {
                       translate_menu.setTitle(R.string.menu_translate);
                   }
            }
    }

    @Override
    public void loadingStateChanged(boolean isLoading) {
        if (mReloadPropertyModel != null) {
            Resources resources = mContext.getResources();
            mReloadPropertyModel.get(AppMenuItemProperties.ICON)
                    .setLevel(isLoading
                                    ? resources.getInteger(R.integer.reload_button_level_stop)
                                    : resources.getInteger(R.integer.reload_button_level_reload));
            mReloadPropertyModel.set(AppMenuItemProperties.TITLE,
                    resources.getString(isLoading ? R.string.accessibility_btn_stop_loading
                                                  : R.string.accessibility_btn_refresh));
            mReloadPropertyModel.set(AppMenuItemProperties.TITLE_CONDENSED,
                    resources.getString(isLoading ? R.string.menu_stop_refresh : R.string.refresh));
        }
    }

    @Override
    public void onMenuDismissed() {
        mReloadPropertyModel = null;
        if (mUpdateMenuItemVisible) {
            UpdateMenuItemHelper.getInstance().onMenuDismissed();
            UpdateMenuItemHelper.getInstance().unregisterObserver(mAppMenuInvalidator);
            mUpdateMenuItemVisible = false;
            mAppMenuInvalidator = null;
        }
    }

    @VisibleForTesting
    boolean shouldShowIconRow() {
        boolean shouldShowIconRow = mIsTablet ? mDecorView.getWidth()
                        < DeviceFormFactor.getNonMultiDisplayMinimumTabletWidthPx(mContext)
                                              : !isInStartSurfaceHomepage();

        final boolean isMenuButtonOnTop = mToolbarManager != null;
        shouldShowIconRow &= isMenuButtonOnTop;
        return shouldShowIconRow;
    }

    @Override
    public int getFooterResourceId() {
        return 0;
    }

    @Override
    public int getHeaderResourceId() {
        return 0;
    }

    @Override
    public int getGroupDividerId() {
        return R.id.divider_line_id;
    }

    @Override
    public boolean shouldShowFooter(int maxMenuHeight) {
        return true;
    }

    @Override
    public boolean shouldShowHeader(int maxMenuHeight) {
        return true;
    }

    @Override
    public void onFooterViewInflated(AppMenuHandler appMenuHandler, View view) {}

    @Override
    public void onHeaderViewInflated(AppMenuHandler appMenuHandler, View view) {}

    @Override
    public boolean shouldShowIconBeforeItem() {
        return false;
    }

    @Override
    public void recordHighlightedMenuItemShown(@Nullable @IdRes Integer menuItemId) {
        RecordHistogram.recordEnumeratedHistogram("Mobile.AppMenu.HighlightMenuItem.Shown",
                getUmaEnumForMenuItem(menuItemId), AppMenuHighlightItem.NUM_ENTRIES);
    }

    @Override
    public void recordHighlightedMenuItemClicked(@Nullable @IdRes Integer menuItemId) {
        RecordHistogram.recordEnumeratedHistogram("Mobile.AppMenu.HighlightMenuItem.Clicked",
                getUmaEnumForMenuItem(menuItemId), AppMenuHighlightItem.NUM_ENTRIES);
    }

    @Override
    public boolean isMenuIconAtStart() {
        return false;
    }

    private int getUmaEnumForMenuItem(@Nullable @IdRes Integer menuItemId) {
        if (menuItemId == null) return AppMenuHighlightItem.UNKNOWN;

        if (menuItemId == R.id.downloads_menu_id) {
            return AppMenuHighlightItem.DOWNLOADS;
        } else if (menuItemId == R.id.all_bookmarks_menu_id) {
            return AppMenuHighlightItem.BOOKMARKS;
        } else if (menuItemId == R.id.translate_id) {
            return AppMenuHighlightItem.TRANSLATE;
        } else if (menuItemId == R.id.add_to_homescreen_id) {
            return AppMenuHighlightItem.ADD_TO_HOMESCREEN;
        } else if (menuItemId == R.id.offline_page_id) {
            return AppMenuHighlightItem.DOWNLOAD_THIS_PAGE;
        } else if (menuItemId == R.id.bookmark_this_page_id) {
            return AppMenuHighlightItem.BOOKMARK_THIS_PAGE;
        } else if (menuItemId == R.id.app_menu_footer) {
            return AppMenuHighlightItem.DATA_REDUCTION_FOOTER;
        }
        return AppMenuHighlightItem.UNKNOWN;
    }

    /**
     * Updates the bookmark item's visibility.
     *
     * @param bookmarkMenuItemShortcut {@link MenuItem} for adding/editing the bookmark.
     * @param currentTab Current tab being displayed.
     */
    protected void updateBookmarkMenuItemShortcut(
            MenuItem bookmarkMenuItemShortcut, @Nullable Tab currentTab, boolean fromCCT) {
        if (bookmarkMenuItemShortcut == null) return ;
        if (!fromCCT && BookmarkFeatures.isBookmarkMenuItemAsDedicatedRowEnabled()) {
            if (bookmarkMenuItemShortcut != null)
            bookmarkMenuItemShortcut.setVisible(false);
            return;
        }

        if (!mBookmarkBridgeSupplier.hasValue() || currentTab == null) {
            // If the BookmarkBridge still isn't available, assume the bookmark menu item is not
            // editable.
            bookmarkMenuItemShortcut.setEnabled(false);
        } else {
            bookmarkMenuItemShortcut.setEnabled(
                    mBookmarkBridgeSupplier.get().isEditBookmarksEnabled());
        }

        if (currentTab != null && shouldCheckBookmarkStar(currentTab)) {
            bookmarkMenuItemShortcut.setIcon(R.drawable.btn_star_filled);
            bookmarkMenuItemShortcut.setChecked(true);
            bookmarkMenuItemShortcut.setTitleCondensed(mContext.getString(R.string.edit_bookmark));
        } else {
            bookmarkMenuItemShortcut.setIcon(R.drawable.btn_star);
            bookmarkMenuItemShortcut.setChecked(false);
            bookmarkMenuItemShortcut.setTitleCondensed(mContext.getString(R.string.menu_bookmark));
        }
    }

    /**
     * Updates the bookmark item's visibility.
     *
     * @param bookmarkMenuItemAdd {@link MenuItem} for adding the bookmark.
     * @param bookmarkMenuItemEdit {@link MenuItem} for editing the bookmark.
     * @param currentTab Current tab being displayed.
     */
    protected void updateBookmarkMenuItemRow(
            MenuItem bookmarkMenuItemAdd, MenuItem bookmarkMenuItemEdit, @Nullable Tab currentTab) {
        if (bookmarkMenuItemAdd == null || bookmarkMenuItemEdit == null) return ;
        // If the bookmark menu item row is disabled, then hide both item.
        if (!BookmarkFeatures.isBookmarkMenuItemAsDedicatedRowEnabled()
                || !mBookmarkBridgeSupplier.hasValue() || currentTab == null) {
            bookmarkMenuItemAdd.setVisible(false);
            bookmarkMenuItemEdit.setVisible(false);
            return;
        }

        boolean editEnabled = mBookmarkBridgeSupplier.get().isEditBookmarksEnabled();
        bookmarkMenuItemAdd.setEnabled(editEnabled);
        bookmarkMenuItemEdit.setEnabled(editEnabled);

        boolean shouldCheckBookmarkStar = currentTab != null && shouldCheckBookmarkStar(currentTab);
        bookmarkMenuItemAdd.setVisible(!shouldCheckBookmarkStar);
        bookmarkMenuItemEdit.setVisible(shouldCheckBookmarkStar);
    }

    /**
     * Updates the bookmark item's visibility.
     *
     * @param readingListMenuItemAdd {@link MenuItem} for adding to the reading list.
     * @param readingListMenuItemDelete {@link MenuItem} for deleting from the reading list.
     * @param currentTab Current tab being displayed.
     */
    protected void updateReadingListMenuItemRow(@NonNull MenuItem readingListMenuItemAdd,
            @NonNull MenuItem readingListMenuItemDelete, @Nullable Tab currentTab) {
        // If the reading list item row is disabled, then hide both items.
        if (!ReadingListFeatures.isReadingListMenuItemAsDedicatedRowEnabled()
                || !mBookmarkBridgeSupplier.hasValue() || currentTab == null
                || !ReadingListUtils.isReadingListSupported(currentTab.getUrl())
                || mIsTypeSpecificBookmarkItemRowPresent) {
            readingListMenuItemAdd.setVisible(false);
            readingListMenuItemDelete.setVisible(false);
            return;
        }

        mIsTypeSpecificBookmarkItemRowPresent = true;

        boolean editEnabled = mBookmarkBridgeSupplier.get().isEditBookmarksEnabled();
        readingListMenuItemAdd.setEnabled(editEnabled);
        readingListMenuItemDelete.setEnabled(editEnabled);

        boolean readingListItemExists = shouldHighlightReadingList(currentTab);
        readingListMenuItemAdd.setVisible(!readingListItemExists);
        readingListMenuItemDelete.setVisible(readingListItemExists);
    }

    /**
     * Updates the price-tracking menu item visibility.
     *
     * @param startPriceTrackingMenuItem The menu item to start price tracking.
     * @param stopPriceTrackingMenuItem The menu item to stop price tracking.
     * @param currentTab Current tab being displayed.
     */
    protected void updatePriceTrackingMenuItemRow(@NonNull MenuItem startPriceTrackingMenuItem,
            @NonNull MenuItem stopPriceTrackingMenuItem, @Nullable Tab currentTab) {
        ShoppingService service =
                ShoppingServiceFactory.getForProfile(Profile.getLastUsedRegularProfile());
        ShoppingService.ProductInfo info = null;
        if (service != null && currentTab != null) {
            info = service.getAvailableProductInfoForUrl(currentTab.getUrl());
        }

        // If price tracking isn't enabled or the page isn't eligible, then hide both items.
        if (!ShoppingFeatures.isShoppingListEnabled()
                || !PowerBookmarkUtils.isPriceTrackingEligible(currentTab)
                || mIsTypeSpecificBookmarkItemRowPresent) {
            startPriceTrackingMenuItem.setVisible(false);
            stopPriceTrackingMenuItem.setVisible(false);
            return;
        }

        PowerBookmarkMeta existingBookmarkMeta = PowerBookmarkUtils.getBookmarkBookmarkMetaForTab(
                mBookmarkBridgeSupplier.get(), currentTab);
        if (existingBookmarkMeta != null && !existingBookmarkMeta.hasShoppingSpecifics()) {
            startPriceTrackingMenuItem.setVisible(false);
            stopPriceTrackingMenuItem.setVisible(false);
            return;
        }

        mIsTypeSpecificBookmarkItemRowPresent = true;

        boolean editEnabled = mBookmarkBridgeSupplier.get().isEditBookmarksEnabled();
        startPriceTrackingMenuItem.setEnabled(editEnabled);
        stopPriceTrackingMenuItem.setEnabled(editEnabled);

        boolean priceTrackingEnabled = false;
        if (info != null) {
            priceTrackingEnabled = PowerBookmarkUtils.isPriceTrackingEnabledForClusterId(
                    info.productClusterId, mBookmarkBridgeSupplier.get());
        }
        startPriceTrackingMenuItem.setVisible(!priceTrackingEnabled);
        stopPriceTrackingMenuItem.setVisible(priceTrackingEnabled);
    }

    /**
     * Updates the request desktop site item's state.
     *
     * @param menu {@link Menu} for request desktop site.
     * @param currentTab Current tab being displayed.
     * @param canShowRequestDesktopSite If the request desktop site menu item should show or not.
     * @param isChromeScheme Whether URL for the current tab starts with the chrome:// scheme.
     */
    protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab currentTab,
            boolean canShowRequestDesktopSite, boolean isChromeScheme) {
        MenuItem requestMenuRow = menu.findItem(R.id.request_desktop_site_row_menu_id);
        MenuItem requestMenuLabel = menu.findItem(R.id.request_desktop_site_id);
        MenuItem requestMenuCheck = menu.findItem(R.id.request_desktop_site_check_id);

        // Hide request desktop site on all chrome:// pages except for the NTP. If
        // REQUEST_DESKTOP_SITE_EXCEPTIONS is enabled, hide the entry for all native pages.
        boolean itemVisible = currentTab != null && canShowRequestDesktopSite
                && (!isChromeScheme
                        || (!ContentFeatureList.isEnabled(
                                    ContentFeatureList.REQUEST_DESKTOP_SITE_EXCEPTIONS)
                                && currentTab.isNativePage()))
                && !shouldShowReaderModePrefs(currentTab) && currentTab.getWebContents() != null;

        requestMenuRow.setVisible(itemVisible);
        if (!itemVisible) return;

        boolean isRequestDesktopSite =
                currentTab.getWebContents().getNavigationController().getUseDesktopUserAgent();
        if (ChromeFeatureList.sAppMenuMobileSiteOption.isEnabled()) {
            requestMenuLabel.setTitle(isRequestDesktopSite
                            ? R.string.menu_item_request_mobile_site
                            : R.string.menu_item_request_desktop_site);
            requestMenuLabel.setIcon(isRequestDesktopSite ? R.drawable.smartphone_black_24dp
                                                          : R.drawable.ic_desktop_windows);
            requestMenuCheck.setVisible(false);
        } else {
            requestMenuLabel.setTitle(R.string.menu_request_desktop_site);
            requestMenuCheck.setVisible(true);
            // Mark the checkbox if RDS is activated on this page.
            requestMenuCheck.setChecked(isRequestDesktopSite);

            // This title doesn't seem to be displayed by Android, but it is used to set up
            // accessibility text in {@link AppMenuAdapter#setupMenuButton}.
            requestMenuLabel.setTitleCondensed(isRequestDesktopSite
                            ? mContext.getString(R.string.menu_request_desktop_site_on)
                            : mContext.getString(R.string.menu_request_desktop_site_off));
        }
    }

    /**
     * Updates the auto dark menu item's state.
     *
     * @param menu {@link Menu} for auto dark.
     * @param currentTab Current tab being displayed.
     * @param isChromeScheme Whether URL for the current tab starts with the chrome:// scheme.
     */
    protected void updateAutoDarkMenuItem(
            Menu menu, @Nullable Tab currentTab, boolean isChromeScheme) {
        MenuItem autoDarkMenuRow = menu.findItem(R.id.auto_dark_web_contents_row_menu_id);
        MenuItem autoDarkMenuCheck = menu.findItem(R.id.auto_dark_web_contents_check_id);

        // Hide app menu item if on non-NTP chrome:// page or auto dark not enabled.
        boolean isAutoDarkEnabled = isAutoDarkWebContentsEnabled();
        boolean itemVisible = currentTab != null && !isChromeScheme && isAutoDarkEnabled;
        itemVisible = false;
        if (autoDarkMenuRow != null)
            autoDarkMenuRow.setVisible(itemVisible);
        if (!itemVisible) return;

        // Set text based on if site is blocked or not.
        boolean isEnabled = WebContentsDarkModeController.isEnabledForUrl(
                mTabModelSelector.getCurrentModel().getProfile(), currentTab.getUrl());
        autoDarkMenuCheck.setChecked(isEnabled);
    }

    protected void updateManagedByMenuItem(Menu menu, @Nullable Tab currentTab) {
        MenuItem managedByDividerLine = menu.findItem(R.id.managed_by_divider_line_id);
        MenuItem managedByMenuItem = menu.findItem(R.id.managed_by_menu_id);

        boolean managedByMenuItemVisible =
                currentTab != null && shouldShowManagedByMenuItem(currentTab);

        managedByDividerLine.setVisible(managedByMenuItemVisible);
        managedByMenuItem.setVisible(managedByMenuItemVisible);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public boolean isIncognitoEnabled() {
        return IncognitoUtils.isIncognitoModeEnabled();
    }

    @VisibleForTesting
    static void setPageBookmarkedForTesting(Boolean bookmarked) {
        sItemBookmarkedForTesting = bookmarked;
    }

    @VisibleForTesting
    static void setPageInReadingListForTesting(Boolean highlight) {
        sItemInReadingListForTesting = highlight;
    }

    @VisibleForTesting
    void setStartSurfaceStateForTesting(@StartSurfaceState int state) {
        mStartSurfaceState = state;
    }

    void setBookmarkBridgeSupplierForTesting(
            ObservableSupplier<BookmarkBridge> bookmarkBridgeSupplier) {
        mBookmarkBridgeSupplier = bookmarkBridgeSupplier;
    }

    /**
     * @return Whether the menu item's icon need to be tinted to blue.
     */
    protected @ColorRes int getMenuItemIconColorRes(MenuItem menuItem) {
        final int itemId = menuItem.getItemId();
        if (itemId == R.id.edit_bookmark_menu_id || itemId == R.id.delete_from_reading_list_menu_id
                || itemId == R.id.disable_price_tracking_menu_id) {
            return R.color.default_icon_color_accent1_tint_list;
        }
        return R.color.default_icon_color_secondary_tint_list;
    }
}
