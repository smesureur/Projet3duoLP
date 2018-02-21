/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.zkoss.ganttz.resourceload;

import static org.zkoss.ganttz.i18n.I18nHelper._;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.zkoss.ganttz.IChartVisibilityChangedListener;
import org.zkoss.ganttz.data.resourceload.LoadTimeLine;
import org.zkoss.ganttz.timetracker.TimeTracker;
import org.zkoss.ganttz.timetracker.TimeTrackerComponent;
import org.zkoss.ganttz.timetracker.zoom.ZoomLevel;
import org.zkoss.ganttz.util.ComponentsFinder;
import org.zkoss.ganttz.util.Interval;
import org.zkoss.ganttz.util.LongOperationFeedback;
import org.zkoss.ganttz.util.LongOperationFeedback.ILongOperation;
import org.zkoss.ganttz.util.MutableTreeModel;
import org.zkoss.ganttz.util.WeakReferencedListeners;
import org.zkoss.ganttz.util.WeakReferencedListeners.IListenerNotification;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlMacroComponent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Button;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Separator;
import org.zkoss.zul.SimpleListModel;
import org.zkoss.zul.api.Combobox;
import org.zkoss.zul.api.Listbox;
import org.zkoss.zul.api.South;
public class ResourcesLoadPanel extends HtmlMacroComponent {

    public interface IToolbarCommand {
        public void doAction();

        public String getLabel();

        public String getImage();
    }

    private TimeTrackerComponent timeTrackerComponent;

    private ResourceLoadLeftPane leftPane;

    private ResourceLoadList resourceLoadList;

    private List<LoadTimeLine> groups;

    private MutableTreeModel<LoadTimeLine> treeModel;

    private TimeTracker timeTracker;

    private LocalDate previousStart;

    private Interval previousInterval;

    private final Component componentOnWhichGiveFeedback;

    private WeakReferencedListeners<IFilterChangedListener> zoomListeners = WeakReferencedListeners
            .create();

    private Listbox listZoomLevels;

    private final String FILTER_RESOURCES = _("Resources");
    private final String FILTER_CRITERIA = _("Generic allocation criteria");
    private String feedBackMessage;
    private Boolean filterbyResources;

    private boolean refreshNameFilter = true;
    private int filterByNamePosition = 0;
    private int numberOfGroupsByName = 10;
    private int lastSelectedName = 0;
    private final PaginationType paginationType;

    private WeakReferencedListeners<IPaginationFilterChangedListener> nameFilterListener =
        WeakReferencedListeners.create();

    private Component loadChart;

    private boolean visibleChart = true;

    private WeakReferencedListeners<IChartVisibilityChangedListener> chartVisibilityListeners = WeakReferencedListeners
            .create();

    private final boolean expandResourceLoadViewCharts;

    private Component firstOptionalFilter;

    private Component secondOptionalFilter;

    public ResourcesLoadPanel(List<LoadTimeLine> groups,
            TimeTracker timeTracker, Component componentOnWhichGiveFeedback,
            boolean expandResourceLoadViewCharts, PaginationType paginationType) {
        this.componentOnWhichGiveFeedback = componentOnWhichGiveFeedback;
        this.expandResourceLoadViewCharts = expandResourceLoadViewCharts;
        this.paginationType = paginationType;
        init(groups, timeTracker);
    }

    public void init(List<LoadTimeLine> groups, TimeTracker timeTracker) {
        refreshNameFilter = true;
        this.groups = groups;
        this.timeTracker = timeTracker;
        treeModel = createModelForTree();
        timeTrackerComponent = timeTrackerForResourcesLoadPanel(timeTracker);
        resourceLoadList = new ResourceLoadList(timeTracker, treeModel);
        leftPane = new ResourceLoadLeftPane(treeModel, resourceLoadList);
    }

    public TimeTracker getTimeTracker() {
        return timeTracker;
    }

    public ListModel getFilters() {
        String[] filters = new String[] { FILTER_RESOURCES, FILTER_CRITERIA };
        return new SimpleListModel(filters);
    }

    public void setFilter(String filterby) {
        if (filterby.equals(FILTER_RESOURCES)) {
            this.filterbyResources = true;
            this.feedBackMessage = _("showing resources");
        } else {
            this.filterbyResources = false;
            this.feedBackMessage = _("showing criteria");
        }
        refreshNameFilter = true;
        filterByNamePosition = 0;
        invalidatingChangeHappenedWithFeedback();
    }

    public boolean getFilter() {
        return (filterbyResources == null) ? true : filterbyResources;
    }

    private void invalidatingChangeHappenedWithFeedback() {
        LongOperationFeedback.execute(componentOnWhichGiveFeedback,
                new ILongOperation() {

                    @Override
                    public void doAction() {
                        applyFilter();
                    }

                    @Override
                    public String getName() {
                        return getFeedBackMessage();
                    }
                });
    }

    private String getFeedBackMessage() {
        return feedBackMessage;
    }

    private void applyFilter() {
        zoomListeners
                .fireEvent(new IListenerNotification<IFilterChangedListener>() {
                    @Override
                    public void doNotify(IFilterChangedListener listener) {
                        listener.filterChanged(getFilter());
                    }
                });
    }

    public void addFilterListener(IFilterChangedListener listener) {
        zoomListeners.addListener(listener);
    }

    public ListModel getZoomLevels() {
        ZoomLevel[] selectableZoomlevels = { ZoomLevel.DETAIL_ONE,
                ZoomLevel.DETAIL_TWO, ZoomLevel.DETAIL_THREE,
                ZoomLevel.DETAIL_FOUR, ZoomLevel.DETAIL_FIVE };
        return new SimpleListModel(selectableZoomlevels);
    }

    public void setZoomLevel(final ZoomLevel zoomLevel) {
        savePreviousData();
        getTimeTrackerComponent().updateDayScroll();
        timeTracker.setZoomLevel(zoomLevel);
    }

    public void zoomIncrease() {
        savePreviousData();
        getTimeTrackerComponent().updateDayScroll();
        timeTracker.zoomIncrease();
    }

    public void zoomDecrease() {
        savePreviousData();
        getTimeTrackerComponent().updateDayScroll();
        timeTracker.zoomDecrease();
    }

    private void savePreviousData() {
        TimeTracker timeTracker = getTimeTrackerComponent().getTimeTracker();
        this.previousStart = timeTracker.getRealInterval().getStart();
        this.previousInterval = timeTracker.getMapper().getInterval();
    }

    public LocalDate getPreviousStart() {
        return previousStart;
    }

    public Interval getPreviousInterval() {
        return previousInterval;
    }

    public TimeTrackerComponent getTimeTrackerComponent() {
        return timeTrackerComponent;
    }

    public void add(final IToolbarCommand... commands) {
        Component toolbar = getToolbar();
        resetToolbar(toolbar);
        Separator separator = getSeparator();
        for (IToolbarCommand c : commands) {
            toolbar.insertBefore(asButton(c), separator);
        }
    }

    private void resetToolbar(Component toolbar) {
        List<Component> children = toolbar.getChildren();
        List<Button> buttons = ComponentsFinder.findComponentsOfType(
                Button.class, children);
        for (Button b : buttons) {
            toolbar.removeChild(b);
        }
    }

    private Button asButton(final IToolbarCommand c) {
        Button result = new Button();
        result.addEventListener(Events.ON_CLICK, new EventListener() {
            @Override
            public void onEvent(Event event) {
                c.doAction();
            }
        });
        if (!StringUtils.isEmpty(c.getImage())) {
            result.setImage(c.getImage());
            result.setTooltiptext(c.getLabel());
        } else {
            result.setLabel(c.getLabel());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Separator getSeparator() {
        List<Component> children = getToolbar().getChildren();
        Separator separator = ComponentsFinder.findComponentsOfType(
                Separator.class, children).get(0);
        return separator;
    }

    private Component getToolbar() {
        Component toolbar = getFellow("toolbar");
        return toolbar;
    }

    private MutableTreeModel<LoadTimeLine> createModelForTree() {
        MutableTreeModel<LoadTimeLine> result = MutableTreeModel
                .create(LoadTimeLine.class);
        for (LoadTimeLine loadTimeLine : this.getGroupsToShow()) {
            result.addToRoot(loadTimeLine);
            result = addNodes(result, loadTimeLine);
        }
        return result;
    }


    private MutableTreeModel<LoadTimeLine> addNodes(
            MutableTreeModel<LoadTimeLine> tree, LoadTimeLine parent) {
        if (!parent.getChildren().isEmpty()) {
            tree.add(parent, parent.getChildren());
            for (LoadTimeLine loadTimeLine : parent.getChildren()) {
                tree = addNodes(tree, loadTimeLine);
            }
        }
        return tree;
    }

    private TimeTrackerComponent timeTrackerForResourcesLoadPanel(
            TimeTracker timeTracker) {
        return new TimeTrackerComponent(timeTracker) {
            @Override
            protected void scrollHorizontalPercentage(int daysDisplacement) {
                response("", new AuInvoke(resourceLoadList,
                        "scroll_horizontal", daysDisplacement + ""));
                moveCurrentPositionScroll();
            }

            @Override
            protected void moveCurrentPositionScroll() {
                // get the previous data.
                LocalDate previousStart = getPreviousStart();

                // get the current data
                int diffDays = getTimeTrackerComponent().getDiffDays(
                        previousStart);
                double pixelPerDay = getTimeTrackerComponent().getPixelPerDay();

                response("move_scroll", new AuInvoke(resourceLoadList,
                        "move_scroll", "" + diffDays, "" + pixelPerDay));
            }

            protected void updateCurrentDayScroll() {
                double previousPixelPerDay = getTimeTracker().getMapper()
                        .getPixelsPerDay().doubleValue();

                response("update_day_scroll", new AuInvoke(resourceLoadList,
                        "update_day_scroll", "" + previousPixelPerDay));

            }
        };
    }

    @Override
    public void afterCompose() {
        super.afterCompose();
        clearComponents();
        getFellow("insertionPointLeftPanel").appendChild(leftPane);
        leftPane.afterCompose();

        getFellow("insertionPointRightPanel").appendChild(timeTrackerComponent);
        getFellow("insertionPointRightPanel").appendChild(resourceLoadList);
        TimeTrackerComponent timeTrackerHeader = createTimeTrackerHeader();
        getFellow("insertionPointTimetracker").appendChild(timeTrackerHeader);

        // Insert additional filters if any
        Component additionalFilter = getFirstOptionalFilter();
        if(additionalFilter != null) {
            getFellow("additionalFilterInsertionPoint1").appendChild(additionalFilter);
        }
        additionalFilter = getSecondOptionalFilter();
        if(additionalFilter != null) {
            getFellow("additionalFilterInsertionPoint2").appendChild(additionalFilter);
        }

        timeTrackerHeader.afterCompose();
        timeTrackerComponent.afterCompose();
        listZoomLevels = (Listbox) getFellow("listZoomLevels");
        listZoomLevels.setSelectedIndex(timeTracker.getDetailLevel().ordinal());

        if(paginationType == PaginationType.INTERNAL_PAGINATION && refreshNameFilter) {
            setupNameFilter();
        }
        else if(paginationType == PaginationType.NONE) {
            getFellow("filterByNameCombo").setVisible(false);
            getFellow("filterByNameLabel").setVisible(false);
        }

        getFellow("insertionPointChart").appendChild(loadChart);

        this.visibleChart = expandResourceLoadViewCharts;
        ((South) getFellow("graphics")).setOpen(this.visibleChart);
        savePreviousData();
    }

    public Component getFirstOptionalFilter() {
        return firstOptionalFilter;
    }

    public void setFirstOptionalFilter(Component firstOptionalFilter) {
        this.firstOptionalFilter = firstOptionalFilter;
    }

    public Component getSecondOptionalFilter() {
        return secondOptionalFilter;
    }

    public void setSecondOptionalFilter(Component secondOptionalFilter) {
        this.secondOptionalFilter = secondOptionalFilter;
    }

    public void clearComponents() {
        getFellow("insertionPointLeftPanel").getChildren().clear();
        getFellow("insertionPointRightPanel").getChildren().clear();
        getFellow("insertionPointTimetracker").getChildren().clear();
        getFellow("insertionPointChart").getChildren().clear();
    }

    private TimeTrackerComponent createTimeTrackerHeader() {
        return new TimeTrackerComponent(
                timeTracker) {

         @Override
         protected void scrollHorizontalPercentage(int pixelsDisplacement) {
         }

            @Override
            protected void moveCurrentPositionScroll() {
                // TODO Auto-generated method stub

            }

            @Override
            protected void updateCurrentDayScroll() {
                // TODO Auto-generated method stub

            }
        };
    }

    public void addSeeScheduledOfListener(
            ISeeScheduledOfListener seeScheduledOfListener) {
        leftPane.addSeeScheduledOfListener(seeScheduledOfListener);
        resourceLoadList.addSeeScheduledOfListener(seeScheduledOfListener);
    }

    private void setupNameFilter() {
        Combobox filterByNameCombo = (Combobox) getFellow("filterByNameCombo");
        filterByNameCombo.getChildren().clear();
        int size = groups.size();

        if(size > numberOfGroupsByName) {
            int position = 0;
            while(position < size) {
                String firstName = groups.get(position).getConceptName();
                String lastName;
                int newPosition = position + numberOfGroupsByName;
                if(newPosition - 1 < size) {
                    lastName = groups.get(newPosition - 1)
                    .getConceptName();
                }
                else {
                    lastName = groups.get(size - 1)
                    .getConceptName();
                }

                Comboitem item = new Comboitem();
                item.setLabel(firstName.substring(0, 1) + " - " + lastName.substring(0, 1));
                item.setDescription(firstName + " - " + lastName);
                item.setValue(position);
                filterByNameCombo.appendChild(item);
                position = newPosition;
            }
        }

        Comboitem lastItem = new Comboitem();
        lastItem.setLabel(_("All"));
        lastItem.setDescription(_("Show all elements"));
        lastItem.setValue(new Integer(-1));
        filterByNameCombo.appendChild(lastItem);

        filterByNameCombo.setSelectedIndex(0);
        refreshNameFilter = false;
    }

    /**
     * Returns only the LoadTimeLine objects that have to be show
     * according to the name filter.
     * @return
     */
    private List<LoadTimeLine> getGroupsToShow() {
        if(paginationType != PaginationType.INTERNAL_PAGINATION ||
                filterByNamePosition == -1) {
            return groups;
        }
        int endPosition =
            (filterByNamePosition + numberOfGroupsByName < groups.size())?
                filterByNamePosition + numberOfGroupsByName :
                groups.size();
        return groups.subList(filterByNamePosition, endPosition);
    }

    public void onSelectFilterByName(Combobox comboByName) {
        if (comboByName.getSelectedItemApi() == null) {
            resetComboByName(comboByName);
        } else {
            Integer filterByNamePosition = (Integer) comboByName
                    .getSelectedItemApi().getValue();
            if (paginationType != PaginationType.NONE) {
                this.filterByNamePosition = filterByNamePosition.intValue();
                this.lastSelectedName = comboByName.getSelectedIndex();
                this.feedBackMessage = _("filtering by name");
                changeNameFilterWithFeedback();
            }
        }
    }

    private void resetComboByName(Combobox comboByName) {
        if (this.lastSelectedName == -1) {
            comboByName.setSelectedIndex(0);
        } else {
            comboByName.setSelectedIndex(this.lastSelectedName);
        }
        comboByName.invalidate();
    }

    private void changeNameFilterWithFeedback() {
        LongOperationFeedback.execute(componentOnWhichGiveFeedback,
                new ILongOperation() {

            @Override
            public void doAction() {
                if(paginationType == PaginationType.INTERNAL_PAGINATION) {
                    //if the pagination is internal, we are in charge of repainting the graph
                    treeModel = createModelForTree();
                    timeTrackerComponent = timeTrackerForResourcesLoadPanel(timeTracker);
                    resourceLoadList = new ResourceLoadList(timeTracker, treeModel);
                    leftPane = new ResourceLoadLeftPane(treeModel, resourceLoadList);
                }
                nameFilterListener.fireEvent(new IListenerNotification<IPaginationFilterChangedListener>() {
                    @Override
                    public void doNotify(IPaginationFilterChangedListener listener) {
                        listener.filterChanged(filterByNamePosition);
                    }
                });
                afterCompose();
            }

            @Override
            public String getName() {
                return getFeedBackMessage();
            }
        });
    }

    public void setInternalPaginationDisabled(boolean disabled) {
        Combobox combo = ((Combobox) getFellow("filterByNameCombo"));
        if (combo != null && combo.isDisabled() != disabled) {
            filterByNamePosition = disabled? -1 :
                ((Integer)combo.getSelectedItemApi().getValue()).intValue();
            combo.setDisabled(disabled);
        }
    }

    public void addPaginationFilterListener(
            IPaginationFilterChangedListener iFilterChangedListener) {
        nameFilterListener.addListener(iFilterChangedListener);
    }

    public void changeChartVisibility(boolean visible) {
        visibleChart = visible;
        chartVisibilityListeners
                .fireEvent(new IListenerNotification<IChartVisibilityChangedListener>() {
                    @Override
                    public void doNotify(
                            IChartVisibilityChangedListener listener) {
                        listener.chartVisibilityChanged(visibleChart);
                    }
                });
    }

    public boolean isVisibleChart() {
        return visibleChart;
    }

    public void addChartVisibilityListener(
            IChartVisibilityChangedListener chartVisibilityChangedListener) {
        chartVisibilityListeners.addListener(chartVisibilityChangedListener);
    }

    public void setLoadChart(Component loadChart) {
        this.loadChart = loadChart;
    }

    public int getPaginationFilterPageSize() {
        return numberOfGroupsByName;
    }

    public Combobox getPaginationFilterCombobox() {
        if(paginationType == PaginationType.EXTERNAL_PAGINATION) {
            return (Combobox) getFellow("filterByNameCombo");
        }
        return null;
    }

    public enum PaginationType {
        /**
         * Sets the widget to take care of the pagination of all the LoadTimeLine objects received.
         */
        INTERNAL_PAGINATION,
        /**
         * The widget will only show the combo box but its content has to be configured externally.
         * The pagination has to be managed externally too: the widget will show all the LoadTimeLine
         * objects received.
         */
        EXTERNAL_PAGINATION,
        /**
         * Disables pagination. Shows all the LoadTimeLine objects received.
         */
        NONE;
    }

}