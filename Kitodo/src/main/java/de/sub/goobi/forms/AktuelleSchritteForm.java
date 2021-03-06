/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package de.sub.goobi.forms;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.export.download.TiffHeader;
import de.sub.goobi.helper.BatchStepHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.Page;
import de.sub.goobi.helper.PropertyListObject;
import de.sub.goobi.helper.WebDav;
import de.sub.goobi.metadaten.MetadatenImagesHelper;
import de.sub.goobi.metadaten.MetadatenSperrung;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.goobi.production.flow.jobs.HistoryAnalyserJob;
import org.goobi.production.properties.AccessCondition;
import org.goobi.production.properties.ProcessProperty;
import org.goobi.production.properties.PropertyParser;
import org.kitodo.data.database.beans.Batch;
import org.kitodo.data.database.beans.Batch.Type;
import org.kitodo.data.database.beans.History;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.HistoryTypeEnum;
import org.kitodo.data.database.helper.enums.PropertyType;
import org.kitodo.data.database.helper.enums.TaskEditType;
import org.kitodo.data.database.helper.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.dto.TaskDTO;
import org.kitodo.model.LazyDTOModel;
import org.kitodo.services.ServiceManager;

@Named("AktuelleSchritteForm")
@SessionScoped
public class AktuelleSchritteForm extends BasisForm {
    private static final long serialVersionUID = 5841566727939692509L;
    private static final Logger logger = LogManager.getLogger(AktuelleSchritteForm.class);
    private Process myProcess = new Process();
    private Task mySchritt = new Task();
    private Integer myProblemID;
    private Integer mySolutionID;
    private String problemMessage;
    private String solutionMessage;
    private String modusBearbeiten = "";
    private final WebDav myDav = new WebDav();
    private int gesamtAnzahlImages = 0;
    private int pageAnzahlImages = 0;
    private boolean nurOffeneSchritte = false;
    private boolean nurEigeneSchritte = false;
    private boolean showAutomaticTasks = false;
    private boolean hideCorrectionTasks = false;
    private HashMap<String, Boolean> anzeigeAnpassen;
    private String scriptPath;
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private String addToWikiField = "";
    private static String DONEDIRECTORYNAME = "fertig/";
    private Boolean flagWait = false;
    private final ReentrantLock flagWaitLock = new ReentrantLock();
    private BatchStepHelper batchHelper;
    private Map<Integer, PropertyListObject> containers = new TreeMap<>();
    private Integer container;
    private List<ProcessProperty> processPropertyList;
    private ProcessProperty processProperty;
    private transient ServiceManager serviceManager = new ServiceManager();
    private int stepId;

    /**
     * Constructor.
     */
    public AktuelleSchritteForm() {
        super();
        super.setLazyDTOModel(new LazyDTOModel(serviceManager.getTaskService()));
        this.anzeigeAnpassen = new HashMap<>();
        this.anzeigeAnpassen.put("lockings", false);
        this.anzeigeAnpassen.put("selectionBoxes", false);
        this.anzeigeAnpassen.put("processId", false);
        this.anzeigeAnpassen.put("modules", false);
        this.anzeigeAnpassen.put("batchId", false);
        /*
         * Vorgangsdatum generell anzeigen?
         */
        LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
        if (login != null && login.getMyBenutzer() != null) {
            this.anzeigeAnpassen.put("processDate", login.getMyBenutzer().isConfigProductionDateShow());
        } else {
            this.anzeigeAnpassen.put("processDate", false);
        }
        DONEDIRECTORYNAME = ConfigCore.getParameter("doneDirectoryName", "fertig/");
    }

    /**
     * Anzeige der Schritte.
     */
    public String filterAll() {
        try {
            List<TaskDTO> tasks;
            if (!showAutomaticTasks) {
                if (hideCorrectionTasks) {
                    tasks = serviceManager.getTaskService().findOpenNotAutomaticTasksWithoutCorrectionForCurrentUser(sortList());
                } else {
                    tasks = serviceManager.getTaskService().findOpenNotAutomaticTasksForCurrentUser(sortList());
                }
            } else {
                if (hideCorrectionTasks) {
                    tasks = serviceManager.getTaskService().findOpenTasksWithoutCorrectionForCurrentUser(sortList());
                } else {
                    tasks = serviceManager.getTaskService().findOpenTasksForCurrentUser(sortList());
                }
            }
            this.page = new Page<>(0, tasks);
        } catch (DataException e) {
            Helper.setFehlerMeldung("Error on reading ElasticSearch: ", e.getMessage());
            return null;
        }
        return "/pages/AktuelleSchritteAlle";
    }

    /**
     * This method initializes the task list without any filter whenever the
     * bean is created.
     */
    @PostConstruct
    public void initializeTaskList() {
        filterAll();
    }

    /**
     * It is possible that sorting by related object can be hard to achieve. Right
     * now it is replaced with sorting by id.
     *
     * @return sort clause for query
     */
    private String sortList() {
        String sort = SortBuilders.fieldSort("priority").order(SortOrder.ASC).toString();
        // TODO: find out if it's possible to sort by related objects
        // Order order = Order.asc("proc.title");
        if (this.sortierung.equals("schrittAsc")) {
            sort += ", " + SortBuilders.fieldSort("title").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("schrittDesc")) {
            sort += ", " + SortBuilders.fieldSort("title").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("prozessAsc")) {
            sort += ", " + SortBuilders.fieldSort("process").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("prozessDesc")) {
            sort += ", " + SortBuilders.fieldSort("process").order(SortOrder.DESC).toString();
        }
        /*if (this.sortierung.equals("batchAsc")) {
            order = Order.asc("proc.batchID");
        }
        if (this.sortierung.equals("batchDesc")) {
            order = Order.desc("proc.batchID");
        }
        if (this.sortierung.equals("prozessdateAsc")) {
            order = Order.asc("proc.creationDate");
        }
        if (this.sortierung.equals("prozessdateDesc")) {
            order = Order.desc("proc.creationDate");
        }*/
        if (this.sortierung.equals("projektAsc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("projektDesc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("modulesAsc")) {
            sort += ", " + SortBuilders.fieldSort("typeModuleName").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("modulesDesc")) {
            sort += ", " + SortBuilders.fieldSort("typeModuleName").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("statusAsc")) {
            sort += ", " + SortBuilders.fieldSort("processingStatus").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("statusDesc")) {
            sort += ", " + SortBuilders.fieldSort("processingStatus").order(SortOrder.DESC).toString();
        }
        return sort;
    }

    /**
     * Bearbeitung des Schritts übernehmen oder abschliessen.
     */
    public String schrittDurchBenutzerUebernehmen() {
        this.flagWaitLock.lock();
        try {
            if (!this.flagWait) {
                this.flagWait = true;

                // Helper.getHibernateSession().clear();
                Helper.getHibernateSession().refresh(this.mySchritt);

                if (this.mySchritt.getProcessingStatusEnum() != TaskStatus.OPEN) {
                    Helper.setFehlerMeldung("stepInWorkError");
                    this.flagWait = false;
                    return null;
                } else {
                    this.mySchritt.setProcessingStatusEnum(TaskStatus.INWORK);
                    this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
                    mySchritt.setProcessingTime(new Date());
                    User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                    if (ben != null) {
                        mySchritt.setProcessingUser(ben);
                    }
                    if (this.mySchritt.getProcessingBegin() == null) {
                        Date myDate = new Date();
                        this.mySchritt.setProcessingBegin(myDate);
                    }
                    this.mySchritt.getProcess().getHistory().add(new History(this.mySchritt.getProcessingBegin(),
                                    this.mySchritt.getOrdering().doubleValue(), this.mySchritt.getTitle(),
                                    HistoryTypeEnum.taskInWork, this.mySchritt.getProcess()));
                    try {
                        /*
                         * den Prozess aktualisieren, so dass der Sortierungshelper gespeichert wird
                         */
                        this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
                    } catch (DataException e) {
                        Helper.setFehlerMeldung(Helper.getTranslation("stepSaveError"), e);
                        logger.error("Task couldn't get saved", e);
                    } finally {
                        this.flagWait = false;
                    }
                    /*
                     * wenn es ein Image-Schritt ist, dann gleich die Images ins Home
                     */

                    if (this.mySchritt.isTypeImagesRead() || this.mySchritt.isTypeImagesWrite()) {
                        downloadToHome();
                    }
                }
            } else {
                Helper.setFehlerMeldung("stepInWorkError");
                return null;
            }
            this.flagWait = false;
        } finally {
            this.flagWaitLock.unlock();
        }
        return "/pages/AktuelleSchritteBearbeiten";
    }

    /**
     * Edit task.
     *
     * @return page
     */
    public String editStep() {

        Helper.getHibernateSession().refresh(mySchritt);

        return "/pages/AktuelleSchritteBearbeiten";
    }

    /**
     * Take over batch.
     *
     * @return page
     */
    public String takeOverBatch() {
        // find all steps with same batch id and step status
        List<Task> currentStepsOfBatch = new ArrayList<>();

        String taskTitle = this.mySchritt.getTitle();
        List<Batch> batches = serviceManager.getProcessService().getBatchesByType(mySchritt.getProcess(),
                Type.LOGISTIC);
        if (batches.size() > 1) {
            Helper.setFehlerMeldung("multipleBatchesAssigned");
            return null;
        }
        if (batches.size() != 0) {
            Integer batchNumber = batches.iterator().next().getId();
            // only steps with same title
            currentStepsOfBatch = serviceManager.getTaskService().getCurrentTasksOfBatch(taskTitle, batchNumber);
        } else {
            return schrittDurchBenutzerUebernehmen();
        }
        // if only one step is assigned for this batch, use the single

        // Helper.setMeldung("found " + currentStepsOfBatch.size() + " elements
        // in batch");
        if (currentStepsOfBatch.size() == 0) {
            return null;
        }
        if (currentStepsOfBatch.size() == 1) {
            return schrittDurchBenutzerUebernehmen();
        }

        for (Task s : currentStepsOfBatch) {
            if (s.getProcessingStatusEnum().equals(TaskStatus.OPEN)) {
                s.setProcessingStatusEnum(TaskStatus.INWORK);
                s.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                s.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    s.setProcessingUser(ben);
                }
                if (s.getProcessingBegin() == null) {
                    Date myDate = new Date();
                    s.setProcessingBegin(myDate);
                }
                s.getProcess().getHistory().add(new History(s.getProcessingBegin(), s.getOrdering().doubleValue(),
                        s.getTitle(), HistoryTypeEnum.taskInWork, s.getProcess()));

                if (s.isTypeImagesRead() || s.isTypeImagesWrite()) {
                    try {
                        new File(serviceManager.getProcessService().getImagesOrigDirectory(false, s.getProcess()));
                    } catch (Exception e1) {
                        logger.error(e1);
                    }
                    s.setProcessingTime(new Date());

                    if (ben != null) {
                        s.setProcessingUser(ben);
                    }
                    this.myDav.downloadToHome(s.getProcess(), !s.isTypeImagesWrite());

                }
            }

            try {
                this.serviceManager.getProcessService().save(s.getProcess());
            } catch (DataException e) {
                Helper.setFehlerMeldung(Helper.getTranslation("stepSaveError"), e);
                logger.error("Task couldn't get saved", e);
            }
        }

        this.setBatchHelper(new BatchStepHelper(currentStepsOfBatch));
        return "/pages/batchesEdit";
    }

    /**
     * Edit batch.
     *
     * @return page
     */
    public String batchesEdit() {
        // find all steps with same batch id and step status
        List<Task> currentStepsOfBatch = new ArrayList<>();

        String taskTitle = this.mySchritt.getTitle();
        List<Batch> batches = serviceManager.getProcessService().getBatchesByType(mySchritt.getProcess(),
                Type.LOGISTIC);
        if (batches.size() > 1) {
            Helper.setFehlerMeldung("multipleBatchesAssigned");
            return null;
        }
        if (batches.size() != 0) {
            Integer batchNumber = batches.iterator().next().getId();
            // only steps with same title
            currentStepsOfBatch = serviceManager.getTaskService().getCurrentTasksOfBatch(taskTitle, batchNumber);
        } else {
            return "/pages/AktuelleSchritteBearbeiten";
        }
        // if only one step is assigned for this batch, use the single

        // Helper.setMeldung("found " + currentStepsOfBatch.size() + " elements
        // in batch");

        if (currentStepsOfBatch.size() == 1) {
            return "/pages/AktuelleSchritteBearbeiten";
        }
        this.setBatchHelper(new BatchStepHelper(currentStepsOfBatch));
        return "/pages/batchesEdit";
    }

    @Deprecated
    public void saveProperties() {
    }

    /**
     * Not sure.
     *
     * @return page
     */
    public String schrittDurchBenutzerZurueckgeben() {
        this.myDav.uploadFromHome(this.mySchritt.getProcess());
        this.mySchritt.setProcessingStatusEnum(TaskStatus.OPEN);
        // mySchritt.setBearbeitungsbenutzer(null);
        // if we have a correction-step here then never remove startdate
        if (serviceManager.getTaskService().isCorrectionStep(this.mySchritt)) {
            this.mySchritt.setProcessingBegin(null);
        }
        this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        mySchritt.setProcessingTime(new Date());
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben != null) {
            mySchritt.setProcessingUser(ben);
        }

        try {
            /*
             * den Prozess aktualisieren, so dass der Sortierungshelper
             * gespeichert wird
             */
            this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
        } catch (DataException e) {
            logger.error("Task couldn't get saved/inserted", e);
        }
        // calcHomeImages();
        return filterAll();
    }

    /**
     * Not sure.
     *
     * @return page
     */
    public String schrittDurchBenutzerAbschliessen() throws DAOException, DataException {

        /*
         * if step allows writing of images, then count all images here
         */
        if (this.mySchritt.isTypeImagesWrite()) {
            try {
                // this.mySchritt.getProzess().setSortHelperImages(
                // FileUtils.getNumberOfFiles(new
                // File(this.mySchritt.getProzess().getImagesOrigDirectory())));
                HistoryAnalyserJob.updateHistory(this.mySchritt.getProcess());
            } catch (Exception e) {
                Helper.setFehlerMeldung("Error while calculation of storage and images", e);
            }
        }

        /*
         * wenn das Resultat des Arbeitsschrittes zunÃ¤chst verifiziert werden
         * soll, dann ggf. das Abschliessen abbrechen
         */
        if (this.mySchritt.isTypeCloseVerify()) {
            /* Metadatenvalidierung */
            if (this.mySchritt.isTypeMetadata() && ConfigCore.getBooleanParameter("useMetadatenvalidierung")) {
                serviceManager.getMetadataValidationService().setAutoSave(true);
                if (!serviceManager.getMetadataValidationService().validate(this.mySchritt.getProcess())) {
                    return null;
                }
            }

            /* Imagevalidierung */
            if (this.mySchritt.isTypeImagesWrite()) {
                MetadatenImagesHelper mih = new MetadatenImagesHelper(null, null);
                try {
                    if (!mih.checkIfImagesValid(this.mySchritt.getProcess().getTitle(), serviceManager
                            .getProcessService().getImagesOrigDirectory(false, this.mySchritt.getProcess()))) {
                        return null;
                    }
                } catch (Exception e) {
                    Helper.setFehlerMeldung("Error on image validation: ", e);
                }
            }
        }

        for (ProcessProperty prop : processPropertyList) {
            if (prop.getCurrentStepAccessCondition().equals(AccessCondition.WRITEREQUIRED)
                    && (prop.getValue() == null || prop.getValue().equals(""))) {
                Helper.setFehlerMeldung(Helper.getTranslation("Eigenschaft") + " " + prop.getName() + " "
                        + Helper.getTranslation("requiredValue"));
                return null;
            } else if (!prop.isValid()) {
                List<String> parameter = new ArrayList<>();
                parameter.add(prop.getName());
                Helper.setFehlerMeldung(Helper.getTranslation("PropertyValidation", parameter));
                return null;
            }
        }

        /*
         * wenn das Ergebnis der Verifizierung ok ist, dann weiter, ansonsten
         * schon vorher draussen
         */
        this.myDav.uploadFromHome(this.mySchritt.getProcess());
        this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        // it returns null! - not possible to close task
        Task t = serviceManager.getTaskService().getById(this.mySchritt.getId());
        serviceManager.getTaskService().close(t, true);
        return filterAll();
    }

    public String sperrungAufheben() {
        MetadatenSperrung.unlockProcess(this.mySchritt.getProcess().getId());
        return null;
    }

    /**
     * Korrekturmeldung an vorherige Schritte.
     */
    public List<Task> getPreviousStepsForProblemReporting() {
        return serviceManager.getTaskService().getPreviousTasksForProblemReporting(
                this.mySchritt.getOrdering(), this.mySchritt.getProcess().getId());
    }

    public int getSizeOfPreviousStepsForProblemReporting() {
        return getPreviousStepsForProblemReporting().size();
    }

    /**
     * Report the problem.
     *
     * @return problem as String
     */
    public String reportProblem() {
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben == null) {
            Helper.setFehlerMeldung("userNotFound");
            return null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("mySchritt.ID: " + this.mySchritt.getId());
            logger.debug("Korrekturschritt.ID: " + this.myProblemID);
        }
        this.myDav.uploadFromHome(this.mySchritt.getProcess());
        Date myDate = new Date();
        this.mySchritt.setProcessingStatusEnum(TaskStatus.LOCKED);
        this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        mySchritt.setProcessingTime(new Date());
        mySchritt.setProcessingUser(ben);
        this.mySchritt.setProcessingBegin(null);

        try {
            Task temp = serviceManager.getTaskService().getById(this.myProblemID);
            temp.setProcessingStatusEnum(TaskStatus.OPEN);
            temp = serviceManager.getTaskService().setCorrectionStep(temp);
            temp.setProcessingEnd(null);

            Property processProperty = new Property();
            processProperty.setTitle(Helper.getTranslation("Korrektur notwendig"));
            processProperty.setValue("[" + this.formatter.format(new Date()) + ", "
                    + serviceManager.getUserService().getFullName(ben) + "] " + this.problemMessage);
            processProperty.setType(PropertyType.messageError);
            processProperty.getProcesses().add(this.mySchritt.getProcess());
            this.mySchritt.getProcess().getProperties().add(processProperty);

            String message = Helper.getTranslation("KorrekturFuer") + " " + temp.getTitle() + ": " + this.problemMessage
                    + " (" + serviceManager.getUserService().getFullName(ben) + ")";
            this.mySchritt.getProcess().setWikiField(WikiFieldHelper.getWikiMessage(this.mySchritt.getProcess(),
                    this.mySchritt.getProcess().getWikiField(), "error", message));
            serviceManager.getTaskService().save(temp);
            this.mySchritt.getProcess().getHistory().add(new History(myDate, temp.getOrdering().doubleValue(),
                    temp.getTitle(), HistoryTypeEnum.taskError, temp.getProcess()));
            /*
             * alle Schritte zwischen dem aktuellen und dem Korrekturschritt
             * wieder schliessen
             */
            List<Task> allTasksInBetween = serviceManager.getTaskService().getAllTasksInBetween(
                    temp.getOrdering(), this.mySchritt.getOrdering(), this.mySchritt.getProcess().getId());
            for (Task task : allTasksInBetween) {
                task.setProcessingStatusEnum(TaskStatus.LOCKED);
                task = serviceManager.getTaskService().setCorrectionStep(task);
                task.setProcessingEnd(null);
                serviceManager.getTaskService().save(task);
            }

            /*
             * den Prozess aktualisieren, so dass der Sortierungshelper
             * gespeichert wird
             */
            this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
        } catch (DAOException | DataException e) {
            logger.error("Task couldn't get saved/inserted", e);
        }

        this.problemMessage = "";
        this.myProblemID = 0;
        return filterAll();
    }

    /**
     * Problem-behoben-Meldung an nachfolgende Schritte.
     */
    public List<Task> getNextStepsForProblemSolution() {
        return serviceManager.getTaskService().getNextTasksForProblemSolution(
                this.mySchritt.getOrdering(), this.mySchritt.getProcess().getId());
    }

    public int getSizeOfNextStepsForProblemSolution() {
        return getNextStepsForProblemSolution().size();
    }

    /**
     * Solve problem.
     *
     * @return String
     */
    public String solveProblem() {
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben == null) {
            Helper.setFehlerMeldung("userNotFound");
            return null;
        }
        Date now = new Date();
        this.myDav.uploadFromHome(this.mySchritt.getProcess());
        this.mySchritt.setProcessingStatusEnum(TaskStatus.DONE);
        this.mySchritt.setProcessingEnd(now);
        this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        mySchritt.setProcessingTime(new Date());
        mySchritt.setProcessingUser(ben);

        try {
            Task temp = serviceManager.getTaskService().getById(this.mySolutionID);
            /*
             * alle Schritte zwischen dem aktuellen und dem Korrekturschritt
             * wieder schliessen
             */
            List<Task> allTasksInBetween = serviceManager.getTaskService().getAllTasksInBetween(
                    temp.getOrdering(), this.mySchritt.getOrdering(), this.mySchritt.getProcess().getId());
            for (Task task : allTasksInBetween) {
                task.setProcessingStatusEnum(TaskStatus.DONE);
                task.setProcessingEnd(now);
                task.setPriority(0);
                if (task.getId().intValue() == temp.getId().intValue()) {
                    task.setProcessingStatusEnum(TaskStatus.OPEN);
                    task = serviceManager.getTaskService().setCorrectionStep(task);
                    task.setProcessingEnd(null);
                    // step.setBearbeitungsbeginn(null);
                    task.setProcessingTime(now);
                }
                mySchritt.setProcessingTime(new Date());
                mySchritt.setProcessingUser(ben);
                serviceManager.getTaskService().save(task);
            }

            /*
             * den Prozess aktualisieren, so dass der Sortierungshelper
             * gespeichert wird
             */
            String message = Helper.getTranslation("KorrekturloesungFuer") + " " + temp.getTitle() + ": "
                    + this.solutionMessage + " (" + serviceManager.getUserService().getFullName(ben) + ")";
            this.mySchritt.getProcess().setWikiField(WikiFieldHelper.getWikiMessage(this.mySchritt.getProcess(),
                    this.mySchritt.getProcess().getWikiField(), "info", message));

            Property processProperty = new Property();
            processProperty.setTitle(Helper.getTranslation("Korrektur durchgefuehrt"));
            processProperty.setValue(
                    "[" + this.formatter.format(new Date()) + ", " + serviceManager.getUserService().getFullName(ben)
                            + "] " + Helper.getTranslation("KorrekturloesungFuer") + " " + temp.getTitle() + ": "
                            + this.solutionMessage);
            processProperty.setType(PropertyType.messageImportant);
            processProperty.getProcesses().add(this.mySchritt.getProcess());
            this.mySchritt.getProcess().getProperties().add(processProperty);

            this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
        } catch (DAOException | DataException e) {
            logger.error("task couldn't get saved/inserted", e);
        }

        this.solutionMessage = "";
        this.mySolutionID = 0;
        return filterAll();
    }

    /**
     * Upload und Download der Images.
     */
    public String uploadFromHome() {
        mySchritt.setProcessingTime(new Date());
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben != null) {
            mySchritt.setProcessingUser(ben);
        }
        this.myDav.uploadFromHome(this.mySchritt.getProcess());
        Helper.setMeldung(null, "Removed directory from user home", this.mySchritt.getProcess().getTitle());
        return null;
    }

    /**
     * Download to home.
     *
     * @return String
     */
    public String downloadToHome() {
        try {
            new File(serviceManager.getProcessService().getImagesOrigDirectory(false, this.mySchritt.getProcess()));
        } catch (Exception e1) {
            logger.error(e1);
        }
        mySchritt.setProcessingTime(new Date());
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben != null) {
            mySchritt.setProcessingUser(ben);
        }
        this.myDav.downloadToHome(this.mySchritt.getProcess(), !this.mySchritt.isTypeImagesWrite());

        return null;
    }

    /**
     * Upload from home.
     *
     * @return String
     */
    @SuppressWarnings("unchecked")
    public String uploadFromHomeAlle() throws NumberFormatException, DAOException, DataException {
        List<URI> fertigListe = this.myDav.uploadAllFromHome(DONEDIRECTORYNAME);
        List<URI> geprueft = new ArrayList<>();
        /*
         * die hochgeladenen Prozess-IDs durchlaufen und auf abgeschlossen
         * setzen
         */
        if (fertigListe.size() > 0 && this.nurOffeneSchritte) {
            this.nurOffeneSchritte = false;
            filterAll();
        }
        for (URI element : fertigListe) {
            String myID = element.toString()
                    .substring(element.toString().indexOf("[") + 1, element.toString().indexOf("]")).trim();

            for (Task step : (Iterable<Task>) this.page.getCompleteList()) {
                /*
                 * nur wenn der Schritt bereits im Bearbeitungsmodus ist,
                 * abschliessen
                 */
                if (step.getProcess().getId() == Integer.parseInt(myID)
                        && step.getProcessingStatusEnum() == TaskStatus.INWORK) {
                    this.mySchritt = step;
                    if (!schrittDurchBenutzerAbschliessen().isEmpty()) {
                        geprueft.add(element);
                    }
                    this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                }
            }
        }

        this.myDav.removeAllFromHome(geprueft, URI.create(DONEDIRECTORYNAME));
        Helper.setMeldung(null, "removed " + geprueft.size() + " directories from user home:", DONEDIRECTORYNAME);
        return null;
    }

    /**
     * Download to home page.
     *
     * @return String
     */
    public String DownloadToHomePage() {
        download();
        // calcHomeImages();
        Helper.setMeldung(null, "Created directies in user home", "");
        return null;
    }

    /**
     * Download to home.
     *
     * @return String
     */
    public String downloadToHomeHits() {
        download();
        // calcHomeImages();
        Helper.setMeldung(null, "Created directories in user home", "");
        return null;
    }

    @SuppressWarnings("unchecked")
    private void download() {
        for (TaskDTO taskDTO : (List<TaskDTO>) this.page.getListReload()) {
            Task task = new Task();
            try {
                task = serviceManager.getTaskService().getById(taskDTO.getId());
            } catch (DAOException e) {
                logger.error(e);
            }
            if (task.getProcessingStatusEnum() == TaskStatus.OPEN) {
                task.setProcessingStatusEnum(TaskStatus.INWORK);
                task.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                mySchritt.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    mySchritt.setProcessingUser(ben);
                }
                task.setProcessingBegin(new Date());
                Process proz = task.getProcess();
                try {
                    this.serviceManager.getProcessService().save(proz);
                } catch (DataException e) {
                    Helper.setMeldung("fehlerNichtSpeicherbar" + proz.getTitle());
                }
                this.myDav.downloadToHome(proz, false);
            }
        }
    }

    public String getScriptPath() {
        return this.scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    /**
     * Execute script.
     */
    public void executeScript() throws DAOException, DataException {
        Task task = serviceManager.getTaskService().getById(this.mySchritt.getId());
        serviceManager.getTaskService().executeScript(task, this.scriptPath, false);
    }

    @Deprecated
    public int getHomeBaende() {
        return 0;
    }

    public int getAllImages() {
        return this.gesamtAnzahlImages;
    }

    public int getPageImages() {
        return this.pageAnzahlImages;
    }

    /**
     * Calc home images.
     */
    @SuppressWarnings("unchecked")
    public void calcHomeImages() {
        this.gesamtAnzahlImages = 0;
        this.pageAnzahlImages = 0;
        User aktuellerBenutzer = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (aktuellerBenutzer != null && aktuellerBenutzer.isWithMassDownload()) {
            for (TaskDTO taskDTO : (List<TaskDTO>)this.page.getCompleteList()) {
                try {
                    Task task = serviceManager.getTaskService().getById(taskDTO.getId());
                    if (task.getProcessingStatusEnum() == TaskStatus.OPEN) {
                        // gesamtAnzahlImages +=
                        // myDav.getAnzahlImages(step.getProzess().getImagesOrigDirectory());
                        this.gesamtAnzahlImages += serviceManager.getFileService().getSubUris(
                                serviceManager.getProcessService().getImagesOrigDirectory(false, task.getProcess()))
                                .size();
                    }
                } catch (DAOException | IOException e) {
                    logger.error(e);
                }
            }
        }
    }

    public Process getMyProzess() {
        return this.myProcess;
    }

    public void setMyProzess(Process myProzess) {
        this.myProcess = myProzess;
    }

    /**
     * Get my task.
     *
     * @return task
     */
    public Task getMySchritt() {
        try {
            schrittPerParameterLaden();
        } catch (DAOException | NumberFormatException e) {
            logger.error(e);
        }
        return this.mySchritt;
    }

    /**
     * Set my task with edit mode set to empty String.
     *
     * @param task
     *            Object
     */
    public void setMySchritt(Task task) {
        this.modusBearbeiten = "";
        setStep(task);
    }

    /**
     * Set my task.
     *
     * @param task
     *            Object
     */
    public void setStep(Task task) {
        this.mySchritt = task;
        this.mySchritt.setLocalizedTitle(serviceManager.getTaskService().getLocalizedTitle(task.getTitle()));
        loadProcessProperties();
        setAttributesForProcess();
    }

    public Task getStep() {
        return this.mySchritt;
    }

    public String getModusBearbeiten() {
        return this.modusBearbeiten;
    }

    public void setModusBearbeiten(String modusBearbeiten) {
        this.modusBearbeiten = modusBearbeiten;
    }

    public Integer getMyProblemID() {
        return this.myProblemID;
    }

    public void setMyProblemID(Integer myProblemID) {
        this.myProblemID = myProblemID;
    }

    public Integer getMySolutionID() {
        return this.mySolutionID;
    }

    public void setMySolutionID(Integer mySolutionID) {
        this.mySolutionID = mySolutionID;
    }

    public String getProblemMessage() {
        return this.problemMessage;
    }

    public void setProblemMessage(String problemMessage) {
        this.problemMessage = problemMessage;
    }

    public String getSolutionMessage() {
        return this.solutionMessage;
    }

    public void setSolutionMessage(String solutionMessage) {
        this.solutionMessage = solutionMessage;
    }

    private void setAttributesForProcess() {
        Process process = this.mySchritt.getProcess();
        process.setBlockedUser(serviceManager.getProcessService().getBlockedUser(process));
        process.setBlockedMinutes(serviceManager.getProcessService().getBlockedMinutes(process));
        process.setBlockedSeconds(serviceManager.getProcessService().getBlockedSeconds(process));
    }

    /*
     * Parameter per Get Ã¼bergeben bekommen und entsprechen den passenden
     * Schritt laden
     */

    /**
     * prüfen, ob per Parameter vielleicht zunÃ¤chst ein anderer geladen werden
     * soll.
     *
     * @throws DAOException
     *             , NumberFormatException
     */
    private void schrittPerParameterLaden() throws DAOException, NumberFormatException {
        String param = Helper.getRequestParameter("myid");
        if (param != null && !param.equals("")) {
            /*
             * wenn bisher noch keine aktuellen Schritte ermittelt wurden, dann
             * dies jetzt nachholen, damit die Liste vollstÃ¤ndig ist
             */
            if (this.page == null && Helper.getManagedBeanValue("#{LoginForm.myBenutzer}") != null) {
                filterAll();
            }
            Integer inParam = Integer.valueOf(param);
            if (this.mySchritt == null || this.mySchritt.getId() == null || !this.mySchritt.getId().equals(inParam)) {
                this.mySchritt = serviceManager.getTaskService().getById(inParam);
            }
        }
    }

    /**
     * Auswahl mittels Selectboxen.
     */
    @SuppressWarnings("unchecked")
    public void selectionAll() {
        for (TaskDTO task : (List<TaskDTO>) this.page.getList()) {
            task.setSelected(true);
        }
    }

    /**
     * Selection none.
     */
    @SuppressWarnings("unchecked")
    public void selectionNone() {
        for (TaskDTO task : (List<TaskDTO>) this.page.getList()) {
            task.setSelected(false);
        }
    }

    /**
     * Downloads.
     */
    public void downloadTiffHeader() throws IOException {
        TiffHeader tiff = new TiffHeader(this.mySchritt.getProcess());
        tiff.exportStart();
    }

    /**
     * Export DMS.
     */
    public void exportDMS() {
        ExportDms export = new ExportDms();
        try {
            export.startExport(this.mySchritt.getProcess());
        } catch (Exception e) {
            Helper.setFehlerMeldung("Error on export", e.getMessage());
            logger.error(e);
        }
    }

    public boolean isNurOffeneSchritte() {
        return this.nurOffeneSchritte;
    }

    public void setNurOffeneSchritte(boolean nurOffeneSchritte) {
        this.nurOffeneSchritte = nurOffeneSchritte;
    }

    public boolean isNurEigeneSchritte() {
        return this.nurEigeneSchritte;
    }

    public void setNurEigeneSchritte(boolean nurEigeneSchritte) {
        this.nurEigeneSchritte = nurEigeneSchritte;
    }

    public HashMap<String, Boolean> getAnzeigeAnpassen() {
        return this.anzeigeAnpassen;
    }

    public void setAnzeigeAnpassen(HashMap<String, Boolean> anzeigeAnpassen) {
        this.anzeigeAnpassen = anzeigeAnpassen;
    }

    /**
     * Get Wiki field.
     *
     * @return values for wiki field
     */
    public String getWikiField() {
        return this.mySchritt.getProcess().getWikiField();

    }

    /**
     * Sets new value for wiki field.
     *
     * @param inString
     *            input String
     */
    public void setWikiField(String inString) {
        this.mySchritt.getProcess().setWikiField(inString);
    }

    public String getAddToWikiField() {
        return this.addToWikiField;
    }

    public void setAddToWikiField(String addToWikiField) {
        this.addToWikiField = addToWikiField;
    }

    /**
     * Add to wiki field.
     */
    public void addToWikiField() {
        if (addToWikiField != null && addToWikiField.length() > 0) {
            User user = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
            this.mySchritt.setProcess(serviceManager.getProcessService().addToWikiField(this.addToWikiField,
                    this.mySchritt.getProcess()));
            this.addToWikiField = "";
            try {
                this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
            } catch (DataException e) {
                logger.error(e);
            }
        }
    }

    // TODO property

    public ProcessProperty getProcessProperty() {
        return this.processProperty;
    }

    public void setProcessProperty(ProcessProperty processProperty) {
        this.processProperty = processProperty;
    }

    public List<ProcessProperty> getProcessProperties() {
        return this.processPropertyList;
    }

    private void loadProcessProperties() {
        this.containers = new TreeMap<>();
        this.processPropertyList = PropertyParser.getPropertiesForStep(this.mySchritt);

        for (ProcessProperty pt : this.processPropertyList) {
            if (pt.getProzesseigenschaft() == null) {
                Property processProperty = new Property();
                processProperty.getProcesses().add(this.mySchritt.getProcess());
                pt.setProzesseigenschaft(processProperty);
                this.mySchritt.getProcess().getProperties().add(processProperty);
                pt.transfer();
            }
            if (!this.containers.keySet().contains(pt.getContainer())) {
                PropertyListObject plo = new PropertyListObject(pt.getContainer());
                plo.addToList(pt);
                this.containers.put(pt.getContainer(), plo);
            } else {
                PropertyListObject plo = this.containers.get(pt.getContainer());
                plo.addToList(pt);
                this.containers.put(pt.getContainer(), plo);
            }
        }
    }

    /**
     * Save current property.
     */
    public void saveCurrentProperty() {
        List<ProcessProperty> ppList = getContainerProperties();
        for (ProcessProperty pp : ppList) {
            this.processProperty = pp;
            if (isValidProcessProperty()) {
                if (this.processProperty.getProzesseigenschaft() == null) {
                    Property processProperty = new Property();
                    processProperty.getProcesses().add(this.mySchritt.getProcess());
                    this.processProperty.setProzesseigenschaft(processProperty);
                    this.myProcess.getProperties().add(processProperty);
                }
                this.processProperty.transfer();
            } else {
                return;
            }

            this.mySchritt.getProcess().getProperties().add(this.processProperty.getProzesseigenschaft());
            this.processProperty.getProzesseigenschaft().getProcesses().add(this.mySchritt.getProcess());
            try {
                this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
                this.serviceManager.getPropertyService().save(this.processProperty.getProzesseigenschaft());
                Helper.setMeldung("propertySaved");
            } catch (DataException e) {
                logger.error(e);
                Helper.setFehlerMeldung("propertyNotSaved");
            }
        }
        loadProcessProperties();
    }

    private boolean isValidProcessProperty() {
        if (this.processProperty.isValid()) {
            return true;
        } else {
            String value = Helper.getTranslation("propertyNotValid",
                    Arrays.asList(new String[] {processProperty.getName() }));
            Helper.setFehlerMeldung(value);
            Helper.setFehlerMeldung("Property " + this.processProperty.getName() + " is not valid");
            return false;
        }
    }

    public Map<Integer, PropertyListObject> getContainers() {
        return this.containers;
    }

    public List<Integer> getContainerList() {
        return new ArrayList<>(this.containers.keySet());
    }

    /**
     * Get size of properties list.
     *
     * @return size
     */
    public int getPropertyListSize() {
        if (this.processPropertyList == null) {
            return 0;
        }
        return this.processPropertyList.size();
    }

    /**
     * Get sorted properties.
     *
     * @return list of sorted properties
     */
    public List<ProcessProperty> getSortedProperties() {
        Comparator<ProcessProperty> comp = new ProcessProperty.CompareProperties();
        Collections.sort(this.processPropertyList, comp);
        return this.processPropertyList;
    }

    /**
     * Duplicate property.
     */
    public void duplicateProperty() {
        ProcessProperty pt = this.processProperty.getClone(0);
        this.processPropertyList.add(pt);
        this.processProperty = pt;
        saveCurrentProperty();
        loadProcessProperties();
    }

    public BatchStepHelper getBatchHelper() {
        return this.batchHelper;
    }

    public void setBatchHelper(BatchStepHelper batchHelper) {
        this.batchHelper = batchHelper;
    }

    /**
     * Get containerless properties.
     *
     * @return list of properties
     */
    public List<ProcessProperty> getContainerlessProperties() {
        List<ProcessProperty> answer = new ArrayList<>();
        for (ProcessProperty pp : this.processPropertyList) {
            if (pp.getContainer() == 0) {
                answer.add(pp);
            }
        }
        return answer;
    }

    public Integer getContainer() {
        return this.container;
    }

    /**
     * Set container.
     *
     * @param container
     *            Integer
     */
    public void setContainer(Integer container) {
        this.container = container;
        if (container != null && container > 0) {
            this.processProperty = getContainerProperties().get(0);
        }
    }

    /**
     * Get container properties.
     *
     * @return list of properties
     */
    public List<ProcessProperty> getContainerProperties() {
        List<ProcessProperty> answer = new ArrayList<>();
        // int currentContainer = this.processProperty.getContainer();

        if (this.container != null && this.container > 0) {
            for (ProcessProperty pp : this.processPropertyList) {
                if (pp.getContainer() == this.container) {
                    answer.add(pp);
                }
            }
        } else {
            answer.add(this.processProperty);
        }

        return answer;
    }

    /**
     * Duplicate container.
     *
     * @return String
     */
    public String duplicateContainer() {
        Integer currentContainer = this.processProperty.getContainer();
        List<ProcessProperty> plist = new ArrayList<>();
        // search for all properties in container
        for (ProcessProperty pt : this.processPropertyList) {
            if (pt.getContainer() == currentContainer) {
                plist.add(pt);
            }
        }
        int newContainerNumber = 0;
        if (currentContainer > 0) {
            newContainerNumber++;
            // find new unused container number
            boolean search = true;
            while (search) {
                if (!this.containers.containsKey(newContainerNumber)) {
                    search = false;
                } else {
                    newContainerNumber++;
                }
            }
        }
        // clone properties
        for (ProcessProperty pt : plist) {
            ProcessProperty newProp = pt.getClone(newContainerNumber);
            this.processPropertyList.add(newProp);
            this.processProperty = newProp;
            if (this.processProperty.getProzesseigenschaft() == null) {
                Property processProperty = new Property();
                processProperty.getProcesses().add(this.mySchritt.getProcess());
                this.processProperty.setProzesseigenschaft(processProperty);
                this.mySchritt.getProcess().getProperties().add(processProperty);
            }
            this.processProperty.transfer();

        }
        try {
            this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
            Helper.setMeldung("propertySaved");
        } catch (DataException e) {
            logger.error(e);
            Helper.setFehlerMeldung("propertiesNotSaved");
        }
        loadProcessProperties();
        return null;
    }

    public boolean getShowAutomaticTasks() {
        return this.showAutomaticTasks;
    }

    public void setShowAutomaticTasks(boolean showAutomaticTasks) {
        this.showAutomaticTasks = showAutomaticTasks;
    }

    public boolean getHideCorrectionTasks() {
        return hideCorrectionTasks;
    }

    public void setHideCorrectionTasks(boolean hideCorrectionTasks) {
        this.hideCorrectionTasks = hideCorrectionTasks;
    }

    /**
     * Return the id of the current task.
     *
     * @return int stepId
     */
    public int getStepId() {
        return this.stepId;
    }

    /**
     * Set the id of the current task.
     *
     * @param stepId as int
     */
    public void setStepId(int stepId) {
        this.stepId = stepId;
    }

    /**
     * Method being used as viewAction for AktuelleSchritteForm.
     */
    public void loadMyStep() {
        try {
            if (!Objects.equals(this.stepId, null)) {
                setMySchritt(this.serviceManager.getTaskService().getById(this.stepId));
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving task with ID '" + this.stepId + "'; ", e.getMessage());
        }
    }
}
