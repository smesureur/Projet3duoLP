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

package org.libreplan.business.common.test.dbunit;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;
import org.libreplan.business.advance.daos.IAdvanceTypeDAO;
import org.libreplan.business.advance.entities.AdvanceType;
import org.springframework.beans.factory.annotation.Autowired;

public class ExampleDBUnitTest extends AbstractDBUnitTest {

    @Autowired
    private IAdvanceTypeDAO advanceDAO;

    @Test
    @Ignore
    public void percentageInsertedInDB() {
        AdvanceType advance = advanceDAO.findByName("percentage");
        assertEquals("percentage",advance.getUnitName());
    }

}
