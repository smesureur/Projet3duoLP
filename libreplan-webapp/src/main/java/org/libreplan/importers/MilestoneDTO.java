/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2012 Igalia, S.L.
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
package org.libreplan.importers;

import java.util.Date;

import org.libreplan.business.planner.entities.TaskElement;

/**
 * Class that represents no persistent milestones. <br />
 *
 * @author Alba Carro Pérez <alba.carro@gmail.com>
 */
public class MilestoneDTO implements IHasTaskAssociated {

    /**
     * Name of the milestone
     */
    public String name;

    /**
     * Start date of the milestone
     */
    public Date startDate;

    /**
     * String representing the constraint.
     */
    public ConstraintDTO constraint;

    /**
     * String with the date of the constraint.
     */
    public Date constraintDate;

    /**
     * TaskElement created with this data
     */
    public TaskElement taskElement;

    @Override
    public TaskElement getTaskAssociated() {
        return taskElement;
    }
}
