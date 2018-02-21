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
package org.libreplan.web.planner.tabs;

import static org.libreplan.web.I18nHelper._;
import static org.libreplan.web.planner.tabs.MultipleTabsPlannerController.BREADCRUMBS_SEPARATOR;
import static org.libreplan.web.planner.tabs.MultipleTabsPlannerController.getSchedulingLabel;

import java.util.HashMap;
import java.util.Map;

import org.libreplan.business.users.entities.UserRole;
import org.libreplan.web.common.Util;
import org.libreplan.web.limitingresources.LimitingResourcesController;
import org.libreplan.web.planner.tabs.CreatedOnDemandTab.IComponentCreator;
import org.libreplan.web.security.SecurityUtils;
import org.zkoss.ganttz.extensions.ITab;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;

/**
 * @author Lorenzo Tilve Álvaro <ltilve@igalia.com>
 */
public class LimitingResourcesTabCreator {

    public static ITab create(Mode mode,
            LimitingResourcesController LimitingResourcesController,
            LimitingResourcesController LimitingResourcesControllerGlobal,
            Component breadcrumbs) {
        return new LimitingResourcesTabCreator(mode,
                LimitingResourcesController, LimitingResourcesControllerGlobal,
                breadcrumbs)
                .build();
    }

    private final Mode mode;

    private final LimitingResourcesController limitingResourcesController;

    private final LimitingResourcesController limitingResourcesControllerGlobal;

    private final Component breadcrumbs;

    private LimitingResourcesTabCreator(Mode mode,
            LimitingResourcesController LimitingResourcesController,
            LimitingResourcesController LimitingResourcesControllerGlobal,
            Component breadcrumbs) {
        this.mode = mode;
        this.limitingResourcesController = LimitingResourcesController;
        this.limitingResourcesControllerGlobal = LimitingResourcesControllerGlobal;
        this.breadcrumbs = breadcrumbs;
    }

    private ITab build() {
        return TabOnModeType.forMode(mode)
                .forType(ModeType.GLOBAL, createGlobalLimitingResourcesTab())
                .create();
    }

    private ITab createGlobalLimitingResourcesTab() {

        final IComponentCreator componentCreator = new IComponentCreator() {

            @Override
            public org.zkoss.zk.ui.Component create(
                    org.zkoss.zk.ui.Component parent) {
                Map<String, Object> arguments = new HashMap<String, Object>();
                arguments.put("LimitingResourcesController",
                        limitingResourcesControllerGlobal);
                return Executions.createComponents(
                        "/limitingresources/_limitingresources.zul", parent,
                        arguments);
            }

        };
        return new CreatedOnDemandTab(_("Queue-based Resources Planning"),
                "limiting-resources",
                componentCreator) {
            @Override
            protected void beforeShowAction() {
                if (!SecurityUtils
                        .isSuperuserOrUserInRoles(UserRole.ROLE_PLANNING)) {
                    Util.sendForbiddenStatusCodeInHttpServletResponse();
                }
            }

            @Override
            protected void afterShowAction() {
                limitingResourcesControllerGlobal.reload();
                if (breadcrumbs.getChildren() != null) {
                    breadcrumbs.getChildren().clear();
                }
                breadcrumbs.appendChild(new Image(BREADCRUMBS_SEPARATOR));
                breadcrumbs.appendChild(new Label(getSchedulingLabel()));
                breadcrumbs.appendChild(new Image(BREADCRUMBS_SEPARATOR));
                breadcrumbs.appendChild(new Label(
                        _("Queue-based Resources Planning")));
            }
        };
    }

}
