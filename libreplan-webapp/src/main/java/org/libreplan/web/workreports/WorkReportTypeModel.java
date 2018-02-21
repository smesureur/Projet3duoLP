/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2012 Igalia, S.L.
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

package org.libreplan.web.workreports;

import static org.libreplan.web.I18nHelper._;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.hibernate.NonUniqueResultException;
import org.libreplan.business.common.IntegrationEntity;
import org.libreplan.business.common.daos.IConfigurationDAO;
import org.libreplan.business.common.entities.EntityNameEnum;
import org.libreplan.business.common.exceptions.InstanceNotFoundException;
import org.libreplan.business.common.exceptions.ValidationException;
import org.libreplan.business.labels.daos.ILabelTypeDAO;
import org.libreplan.business.labels.entities.Label;
import org.libreplan.business.labels.entities.LabelType;
import org.libreplan.business.workreports.daos.IWorkReportDAO;
import org.libreplan.business.workreports.daos.IWorkReportTypeDAO;
import org.libreplan.business.workreports.entities.PositionInWorkReportEnum;
import org.libreplan.business.workreports.entities.PredefinedWorkReportTypes;
import org.libreplan.business.workreports.entities.WorkReport;
import org.libreplan.business.workreports.entities.WorkReportLabelTypeAssigment;
import org.libreplan.business.workreports.entities.WorkReportType;
import org.libreplan.business.workreports.valueobjects.DescriptionField;
import org.libreplan.web.common.IntegrationEntityModel;
import org.libreplan.web.common.concurrentdetection.OnConcurrentModification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model for UI operations related to {@link WorkReportType}.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Diego Pino García <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@OnConcurrentModification(goToPage = "/workreports/workReportTypes.zul")
public class WorkReportTypeModel extends IntegrationEntityModel implements
        IWorkReportTypeModel {

    @Autowired
    private IWorkReportTypeDAO workReportTypeDAO;

    @Autowired
    private IWorkReportDAO workReportDAO;

    @Autowired
    private ILabelTypeDAO labelTypeDAO;

    @Autowired
    private IConfigurationDAO configurationDAO;

    private WorkReportType workReportType;

    private boolean editing = false;

    private boolean listing = true;

    private static final Map<LabelType, List<Label>> mapLabels = new HashMap<LabelType, List<Label>>();

    @Override
    public WorkReportType getWorkReportType() {
        return this.workReportType;
    }

    @Override
    public Map<LabelType, List<Label>> getMapLabelTypes() {
        final Map<LabelType, List<Label>> result = new HashMap<LabelType, List<Label>>();
        result.putAll(mapLabels);
        return result;
    }

    @Transactional(readOnly = true)
    public boolean thereAreWorkReportsFor() {
        return thereAreWorkReportsFor(getWorkReportType());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean thereAreWorkReportsFor(WorkReportType workReportType) {
        if ((listing) || (isEditing())) {
            final List<WorkReport> workReports = workReportDAO
                    .getAllByWorkReportType(workReportType);
            return (workReports != null && !workReports.isEmpty());
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkReportType> getWorkReportTypesExceptPersonalAndJiraTimesheets() {
        List<WorkReportType> list = workReportTypeDAO.list(WorkReportType.class);
        try {
            list.remove(workReportTypeDAO
                    .findUniqueByName(PredefinedWorkReportTypes.PERSONAL_TIMESHEETS
                            .getName()));
            list.remove(workReportTypeDAO
                    .findUniqueByName(PredefinedWorkReportTypes.JIRA_TIMESHEETS
                            .getName()));
        } catch (NonUniqueResultException e) {
            throw new RuntimeException(e);
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public void prepareForCreate() {
        loadLabels();
        setListing(false);
        editing = false;

        Boolean generateCode = configurationDAO.getConfiguration()
                .getGenerateCodeForWorkReportType();
        this.workReportType = WorkReportType.create("", "");
        if (generateCode) {
            setDefaultCode();
        }
        this.workReportType.setCodeAutogenerated(generateCode);
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(WorkReportType workReportType) {
        if (workReportType.isPersonalTimesheetsType()) {
            throw new IllegalArgumentException(
                    "Personal timesheets timesheet template cannot be edited");
        }
        if (workReportType.isJiraTimesheetsType()) {
            throw new IllegalArgumentException(
                    "Personal timesheets timesheet template cannot be edited");
        }

        setListing(false);
        editing = true;
        Validate.notNull(workReportType);
        loadLabels();

        this.workReportType = getFromDB(workReportType);
        loadCollections(this.workReportType);
        initOldCodes();
    }

    private WorkReportType getFromDB(WorkReportType workReportType) {
        return getFromDB(workReportType.getId());
    }

    @Transactional(readOnly = true)
    private WorkReportType getFromDB(Long id) {
        try {
            WorkReportType result = workReportTypeDAO.find(id);
            return result;
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadCollections(WorkReportType workReportType) {
        for (DescriptionField line : workReportType.getLineFields()) {
            line.getFieldName();
        }

        for (DescriptionField head : workReportType.getHeadingFields()) {
            head.getFieldName();
        }

        for (WorkReportLabelTypeAssigment assignedLabel : workReportType
                .getWorkReportLabelTypeAssigments()) {
            assignedLabel.getDefaultLabel().getName();
            assignedLabel.getLabelType().getName();
        }
    }

    private void loadLabels() {
        mapLabels.clear();
        List<LabelType> labelTypes = labelTypeDAO.getAll();
        for (LabelType labelType : labelTypes) {
            List<Label> labels = new ArrayList<Label>(labelType.getLabels());

            mapLabels.put(labelType, labels);
        }
    }

    @Override
    public void prepareForRemove(WorkReportType workReportType) {
        this.workReportType = workReportType;
    }

    @Override
    @Transactional
    public void save() throws ValidationException {
        workReportTypeDAO.save(workReportType);
    }

    @Override
    @Transactional
    public void confirmRemove(WorkReportType workReportType) {
        if (workReportType.isPersonalTimesheetsType()) {
            throw new IllegalArgumentException(
                    "Personal timesheets timesheet template cannot be removed");
        }
        if (workReportType.isJiraTimesheetsType()) {
            throw new IllegalArgumentException(
                    "Personal timesheets timesheet template cannot be removed");
        }

        try {
            workReportTypeDAO.remove(workReportType.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isEditing() {
        return this.editing;
    }

    @Override
    public void setListing(boolean listing) {
        this.listing = listing;
    }

    /* Operations to manage the Description field */

    public List<DescriptionField> getDescriptionFields() {
        List<DescriptionField> descriptionFields = new ArrayList<DescriptionField>();
        if (getWorkReportType() != null) {
            descriptionFields.addAll(workReportType.getLineFields());
            descriptionFields.addAll(workReportType.getHeadingFields());
        }
        return descriptionFields;
    }

    public void addNewDescriptionField() {
        DescriptionField descriptionField = DescriptionField.create();
        getWorkReportType().addDescriptionFieldToEndLine(descriptionField);
    }

    public void removeDescriptionField(DescriptionField descriptionField) {
        getWorkReportType().removeDescriptionField(descriptionField);
    }

    public void changePositionDescriptionField(
            PositionInWorkReportEnum newPosition,
            DescriptionField descriptionField) {
        getWorkReportType().removeDescriptionField(descriptionField);
        if (newPosition.equals(PositionInWorkReportEnum.HEADING)) {
            getWorkReportType().addDescriptionFieldToEndHead(descriptionField);
        } else {
            getWorkReportType().addDescriptionFieldToEndLine(descriptionField);
        }
    }

    public PositionInWorkReportEnum getPosition(
            DescriptionField descriptionField) {
        if (workReportType.getHeadingFields().contains(descriptionField)) {
            return PositionInWorkReportEnum.HEADING;
        } else {
            return PositionInWorkReportEnum.LINE;
        }
    }

    public boolean isHeadingDescriptionField(DescriptionField descriptionField) {
        return workReportType.getHeadingFields().contains(descriptionField);
    }

    /* Operations to manage the WorkReportLabelTypesAssigment */

    public Set<WorkReportLabelTypeAssigment> getWorkReportLabelTypeAssigments() {
        if (getWorkReportType() != null) {
            return getWorkReportType().getWorkReportLabelTypeAssigments();
        }
        return new HashSet<WorkReportLabelTypeAssigment>();
    }

    public void addNewWorkReportLabelTypeAssigment() {
        if (getWorkReportType() != null) {
            WorkReportLabelTypeAssigment newWorkReportLabelTypeAssigment = WorkReportLabelTypeAssigment
                    .create();
            getWorkReportType().addLabelAssigmentToEndLine(
                    newWorkReportLabelTypeAssigment);
        }
    }

    public void removeWorkReportLabelTypeAssigment(
            WorkReportLabelTypeAssigment workReportLabelTypeAssigment) {
        getWorkReportType().removeLabel(workReportLabelTypeAssigment);
    }

    public PositionInWorkReportEnum getLabelAssigmentPosition(
            WorkReportLabelTypeAssigment workReportLabelTypeAssigment) {
        if (getWorkReportType() != null) {
            return getPosition(workReportLabelTypeAssigment
                    .getLabelsSharedByLines());
        }
        return null;
    }

    public void setLabelAssigmentPosition(
            WorkReportLabelTypeAssigment workReportLabelTypeAssigment,
            PositionInWorkReportEnum position) {
        getWorkReportType().removeLabel(workReportLabelTypeAssigment);
        if (isSharedByLines(position)) {
            getWorkReportType().addLabelAssigmentToEndHead(
                    workReportLabelTypeAssigment);

        } else {
            getWorkReportType().addLabelAssigmentToEndLine(
                    workReportLabelTypeAssigment);
        }
    }

    /* Operation to manage the requirements fields */
    @Override
    public PositionInWorkReportEnum getDatePosition() {
        if (getWorkReportType() != null) {
            return getPosition(getWorkReportType().getDateIsSharedByLines());
        }
        return null;
    }

    @Override
    public PositionInWorkReportEnum getResourcePosition() {
        if (getWorkReportType() != null) {
            return getPosition(getWorkReportType().getResourceIsSharedInLines());
        }
        return null;
    }

    @Override
    public PositionInWorkReportEnum getOrderElementPosition() {
        if (getWorkReportType() != null) {
            return getPosition(getWorkReportType()
                    .getOrderElementIsSharedInLines());
        }
        return null;
    }

    private PositionInWorkReportEnum getPosition(boolean sharedByLines) {
        if (sharedByLines) {
            return PositionInWorkReportEnum.HEADING;
        } else {
            return PositionInWorkReportEnum.LINE;
        }
    }

    @Override
    public void setDatePosition(PositionInWorkReportEnum position) {
        getWorkReportType().setDateIsSharedByLines(isSharedByLines(position));
    }

    @Override
    public void setResourcePosition(PositionInWorkReportEnum position) {
        getWorkReportType().setResourceIsSharedInLines(
                isSharedByLines(position));
    }

    @Override
    public void setOrderElementPosition(PositionInWorkReportEnum position) {
        getWorkReportType().setOrderElementIsSharedInLines(
                isSharedByLines(position));
    }

    private boolean isSharedByLines(PositionInWorkReportEnum position) {
        return PositionInWorkReportEnum.HEADING.equals(position);
    }

    /* Operations that realize the data validations */

    @Transactional(readOnly = true)
    public void validateWorkReportTypeName(String name)
            throws IllegalArgumentException {
        if ((name == null) || (name.isEmpty())) {
            throw new IllegalArgumentException(
                    _("name cannot be empty"));
        }

        getWorkReportType().setName(name);
        if (!getWorkReportType().checkConstraintUniqueWorkReportTypeName()) {
            throw new IllegalArgumentException(
                    _("There is another timesheet template with the same name"));
        }
    }

    @Transactional(readOnly = true)
    public void validateWorkReportTypeCode(String code)
            throws IllegalArgumentException {
        if ((code == null) || (code.isEmpty())) {
            throw new IllegalArgumentException(
                    _("Code cannot be empty"));
        }
        if (code.contains("_")) {
            throw new IllegalArgumentException(
                    _("Value is not valid.\n Code cannot contain chars like '_'."));
        }

        getWorkReportType().setCode(code);
        if (!getWorkReportType().checkConstraintUniqueCode()) {
            throw new IllegalArgumentException(
                    _("There is another timesheet template with the same code"));
        }
    }

    public DescriptionField validateLengthLineFields() {
        for(DescriptionField line : getWorkReportType().getLineFields()){
            if ((line.getLength() == null) || (line.getLength() <= 0)) {
                return line;
            }
        }
        return null;
    }

    public DescriptionField validateFieldNameLineFields() {
        for (DescriptionField line : getDescriptionFields()) {
            if ((line.getFieldName() == null)
                    || (line.getFieldName().isEmpty())
                    || (getWorkReportType().existSameFieldName(line))) {
                return line;
            }
        }
        return null;
    }

    public WorkReportLabelTypeAssigment validateLabelTypes() {
        for (WorkReportLabelTypeAssigment labelTypeAssigment : getWorkReportLabelTypeAssigments()) {
            if ((labelTypeAssigment.getLabelType() == null)
                    || (getWorkReportType()
                            .existRepeatedLabelType(labelTypeAssigment))) {
                return labelTypeAssigment;
            }
        }
        return null;
    }

    public WorkReportLabelTypeAssigment validateLabels() {
        for (WorkReportLabelTypeAssigment labelTypeAssigment : getWorkReportLabelTypeAssigments()) {
            if (labelTypeAssigment.getDefaultLabel() == null) {
                return labelTypeAssigment;
            }
        }
        return null;
    }

    /* Operations to calculated the index position of the fields into workReport */

    public boolean validateTheIndexFieldsAndLabels() {
        return ((getWorkReportType().checkConstraintTheIndexHeadingFieldsAndLabelMustBeUniqueAndConsecutive()) && (getWorkReportType()
                .checkConstraintTheIndexLineFieldsAndLabelMustBeUniqueAndConsecutive()));
    }

    public List<Object> getOrderedListHeading() {
        if (getWorkReportType() != null) {
            return sort(getWorkReportType().getHeadingFieldsAndLabels());
        }
        return new ArrayList<Object>();
    }

    public List<Object> getOrderedListLines() {
        if (getWorkReportType() != null) {
            return sort(getWorkReportType().getLineFieldsAndLabels());
        }
        return new ArrayList<Object>();
    }

    private List<Object> sort(List<Object> list) {
        List<Object> result = new ArrayList<Object>(list);
        for (Object object : list) {
            if ((getIndex(object) >= 0) && (getIndex(object) < list.size())) {
                result.set(getIndex(object), object);
            }
        }
        return result;
    }

    private int getIndex(Object object) {
        if (object instanceof DescriptionField) {
            return ((DescriptionField) object).getPositionNumber();
        } else {
            return ((WorkReportLabelTypeAssigment) object).getPositionNumber();
        }
    }

    public void upFieldOrLabel(Object objectToUp, boolean intoHeading) {
        if (objectToUp instanceof DescriptionField) {
            int newPosition = ((DescriptionField) objectToUp)
                    .getPositionNumber() - 1;
            moveDescriptionField((DescriptionField) objectToUp, intoHeading, newPosition);
        } else {
            int newPosition = ((WorkReportLabelTypeAssigment) objectToUp)
                    .getPositionNumber() - 1;
            moveWorkReportLabelTypeAssigment(
                    (WorkReportLabelTypeAssigment) objectToUp, intoHeading,
                    newPosition);
        }
    }

    public void downFieldOrLabel(Object objectToDown, boolean intoHeading) {
        if (objectToDown instanceof DescriptionField) {
            int newPosition = ((DescriptionField) objectToDown)
                    .getPositionNumber() + 1;
            moveDescriptionField((DescriptionField) objectToDown, intoHeading,
                    newPosition);
        } else {
            int newPosition = ((WorkReportLabelTypeAssigment) objectToDown)
                    .getPositionNumber() + 1;
            moveWorkReportLabelTypeAssigment(
                    (WorkReportLabelTypeAssigment) objectToDown, intoHeading,
                    newPosition);
        }
    }

    private void moveDescriptionField(DescriptionField descriptionField,
            boolean intoHeading, int newPosition) {
        if (intoHeading) {
            getWorkReportType().moveDescriptionFieldToHead(descriptionField,
                    newPosition);
        } else {
            getWorkReportType().moveDescriptionFieldToLine(descriptionField,
                    newPosition);
        }
    }

    private void moveWorkReportLabelTypeAssigment(
            WorkReportLabelTypeAssigment workReportLabelTypeAssigment,
            boolean intoHeading, int newPosition) {
        if (intoHeading) {
            getWorkReportType().moveLabelToHead(workReportLabelTypeAssigment,
                    newPosition);
        } else {
            getWorkReportType().moveLabelToLine(workReportLabelTypeAssigment,
                    newPosition);
        }
    }

    public EntityNameEnum getEntityName() {
        return EntityNameEnum.WORKREPORTTYPE;
    }

    public Set<IntegrationEntity> getChildren() {
        return new HashSet<IntegrationEntity>();
    }

    public IntegrationEntity getCurrentEntity() {
        return this.workReportType;
    }
}
