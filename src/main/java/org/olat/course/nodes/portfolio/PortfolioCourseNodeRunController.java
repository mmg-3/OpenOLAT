/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */

package org.olat.course.nodes.portfolio;

import java.io.File;
import java.util.Date;
import java.util.List;

import org.olat.NewControllerFactory;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.form.flexible.FormItem;
import org.olat.core.gui.components.form.flexible.FormItemContainer;
import org.olat.core.gui.components.form.flexible.elements.FormLink;
import org.olat.core.gui.components.form.flexible.elements.StaticTextElement;
import org.olat.core.gui.components.form.flexible.impl.FormBasicController;
import org.olat.core.gui.components.form.flexible.impl.FormEvent;
import org.olat.core.gui.components.form.flexible.impl.FormLayoutContainer;
import org.olat.core.gui.components.link.Link;
import org.olat.core.gui.components.velocity.VelocityContainer;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.generic.messages.MessageController;
import org.olat.core.gui.control.generic.messages.MessageUIFactory;
import org.olat.core.gui.control.generic.modal.DialogBoxController;
import org.olat.core.gui.control.generic.modal.DialogBoxUIFactory;
import org.olat.core.id.OLATResourceable;
import org.olat.core.id.context.BusinessControl;
import org.olat.core.id.context.BusinessControlFactory;
import org.olat.core.logging.activity.ThreadLocalUserActivityLogger;
import org.olat.core.util.Formatter;
import org.olat.core.util.StringHelper;
import org.olat.core.util.prefs.Preferences;
import org.olat.core.util.resource.OresHelper;
import org.olat.course.CourseModule;
import org.olat.course.assessment.AssessmentHelper;
import org.olat.course.assessment.AssessmentManager;
import org.olat.course.assessment.CourseAssessmentService;
import org.olat.course.assessment.handler.AssessmentConfig;
import org.olat.course.assessment.handler.AssessmentConfig.Mode;
import org.olat.course.highscore.ui.HighScoreRunController;
import org.olat.course.nodes.MSCourseNode;
import org.olat.course.nodes.PortfolioCourseNode;
import org.olat.course.nodes.ms.DocumentsMapper;
import org.olat.course.nodes.portfolio.PortfolioCourseNodeConfiguration.DeadlineType;
import org.olat.course.run.scoring.ScoreAccounting;
import org.olat.course.run.scoring.ScoreEvaluation;
import org.olat.course.run.userview.UserCourseEnvironment;
import org.olat.modules.ModuleConfiguration;
import org.olat.modules.portfolio.Binder;
import org.olat.modules.portfolio.BinderStatus;
import org.olat.modules.portfolio.PortfolioLoggingAction;
import org.olat.modules.portfolio.PortfolioService;
import org.olat.modules.portfolio.handler.BinderTemplateResource;
import org.olat.portfolio.EPLoggingAction;
import org.olat.portfolio.manager.EPFrontendManager;
import org.olat.portfolio.model.structel.EPStructuredMap;
import org.olat.portfolio.model.structel.PortfolioStructureMap;
import org.olat.repository.RepositoryEntry;
import org.olat.util.logging.activity.LoggingResourceable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Description:<br>
 * Portfolio run controller. You can take a map if you are in some learning
 * groups of the course. The controller check if there is a deadline for
 * the map and if yes, set it.
 * 
 * <P>
 * Initial Date:  6 oct. 2010 <br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 */
public class PortfolioCourseNodeRunController extends FormBasicController {

	private PortfolioStructureMap copyMap;
	private PortfolioStructureMap templateMap;
	
	private Binder copyBinder;
	private Binder templateBinder;
	
	private final PortfolioCourseNode courseNode;
	private final ModuleConfiguration config;
	private final OLATResourceable courseOres;
	
	private FormLink newMapLink;
	private FormLink selectMapLink;
	private StaticTextElement newMapMsgEl, deadlineDateText;
	private FormLayoutContainer infosContainer, assessmentInfosContainer;

	private DialogBoxController restoreBinderCtrl;
	
	private Formatter formatter;
	private final UserCourseEnvironment userCourseEnv;
	
	@Autowired
	private EPFrontendManager ePFMgr;
	@Autowired
	private PortfolioService portfolioService;
	@Autowired
	private CourseAssessmentService courseAssessmentService;
	
	public PortfolioCourseNodeRunController(UserRequest ureq, WindowControl wControl, UserCourseEnvironment userCourseEnv,
			PortfolioCourseNode courseNode) {
		super(ureq, wControl, "run");
		
		this.courseNode = courseNode;
		this.config = courseNode.getModuleConfiguration();
		this.userCourseEnv = userCourseEnv;
		
		Long courseResId = userCourseEnv.getCourseEnvironment().getCourseResourceableId();
		courseOres = OresHelper.createOLATResourceableInstance(CourseModule.class, courseResId);
		
		formatter = Formatter.getInstance(getLocale());
		
		RepositoryEntry mapEntry = courseNode.getReferencedRepositoryEntry();
		if(mapEntry != null) {
			if(BinderTemplateResource.TYPE_NAME.equals(mapEntry.getOlatResource().getResourceableTypeName())) {
				templateBinder = portfolioService.getBinderByResource(mapEntry.getOlatResource());
			} else {
				templateMap = (PortfolioStructureMap) ePFMgr.loadPortfolioStructure(mapEntry.getOlatResource());
			}
		}

		initForm(ureq);
	}
	
	@Override
	protected void initForm(FormItemContainer formLayout, Controller listener, UserRequest ureq) {
		if (userCourseEnv.isAdmin() || userCourseEnv.isCoach()) {
			String title = "";
			if(templateMap != null) {
				title = StringHelper.escapeHtml(templateMap.getTitle());
			} else if(templateBinder != null) {
				title = StringHelper.escapeHtml(templateBinder.getTitle());
			}
			MessageController coachMessage = MessageUIFactory.createInfoMessage(ureq, getWindowControl(),
					translate("info.coach.title"), translate("info.coach.text", new String[] { title }));
			(((FormLayoutContainer) formLayout).getFormItemComponent()).put("coachMessage", coachMessage.getInitialComponent());
		}
		
		infosContainer = FormLayoutContainer.createDefaultFormLayout("infos", getTranslator());
		infosContainer.setVisible(userCourseEnv.isParticipant());
		formLayout.add(infosContainer);
		
		String assessmentPage = velocity_root + "/assessment_infos.html";
		assessmentInfosContainer = FormLayoutContainer.createCustomFormLayout("assessmentInfos", getTranslator(), assessmentPage);
		assessmentInfosContainer.setVisible(false);
		formLayout.add(assessmentInfosContainer);
		
		VelocityContainer mainVC = ((FormLayoutContainer) formLayout).getFormItemComponent();
		if (courseNode.getModuleConfiguration().getBooleanSafe(MSCourseNode.CONFIG_KEY_HAS_SCORE_FIELD,false)){
			HighScoreRunController highScoreCtr = new HighScoreRunController(ureq, getWindowControl(), userCourseEnv,
					courseNode, this.mainForm);
			if (highScoreCtr.isViewHighscore()) {
				Component highScoreComponent = highScoreCtr.getInitialComponent();
				mainVC.put("highScore", highScoreComponent);							
			}
		}
		
		Object text = config.get(PortfolioCourseNodeConfiguration.NODE_TEXT);
		String explanation = (text instanceof String) ? (String)text : "";
		if(StringHelper.containsNonWhitespace(explanation)) {
			uifactory.addStaticTextElement("explanation.text", explanation, infosContainer);
		}
		
		String deadlineconfig = (String)config.get(PortfolioCourseNodeConfiguration.DEADLINE_TYPE);
		if (!DeadlineType.none.name().equals(deadlineconfig) && deadlineconfig!=null){
			// show deadline-config
			String deadLineLabel = "map.deadline." + deadlineconfig + ".label";
			String deadLineInfo = "";
			if (deadlineconfig.equals(DeadlineType.absolut.name())){
				Formatter f = Formatter.getInstance(getLocale());
				deadLineInfo = f.formatDate((Date)config.get(PortfolioCourseNodeConfiguration.DEADLINE_DATE));
			} else {
				deadLineInfo = getDeadlineRelativeInfo();
			}
			deadlineDateText = uifactory.addStaticTextElement("deadline", deadLineLabel, deadLineInfo, infosContainer);			
		}
		
		if(templateMap != null || templateBinder != null) {
			updateUI(ureq);
		}
	}
	
	private String getDeadlineRelativeInfo(){
		String[] args = new String[3];
		String month = (String)config.get(PortfolioCourseNodeConfiguration.DEADLINE_MONTH);
		if (StringHelper.containsNonWhitespace(month)) args[0] = translate("map.deadline.info.month", month);
		else args[0] = "";
		String week = (String)config.get(PortfolioCourseNodeConfiguration.DEADLINE_WEEK);
		if (StringHelper.containsNonWhitespace(week)) args[1] = translate("map.deadline.info.week", week);
		else args[1] = "";
		String day = (String)config.get(PortfolioCourseNodeConfiguration.DEADLINE_DAY);
		if (StringHelper.containsNonWhitespace(day)) args[2] = translate("map.deadline.info.day", day);
		else args[2] = "";
		String deadLineInfo = translate("map.deadline.info", args);
		return deadLineInfo;
	}
	
	protected void updateUI(UserRequest ureq) {
		if(templateMap != null) {
			copyMap = ePFMgr.loadPortfolioStructureMap(getIdentity(), templateMap, courseOres, courseNode.getIdent(), null);
		} else if(templateBinder != null) {
			RepositoryEntry courseEntry = userCourseEnv.getCourseEnvironment().getCourseGroupManager().getCourseEntry();
			copyBinder = portfolioService.getBinder(getIdentity(), templateBinder, courseEntry, courseNode.getIdent());
		}
		
		if(copyMap == null && (copyBinder == null || copyBinder.getBinderStatus() == BinderStatus.deleted)) {
			updateEmptyUI();
		} else {
			updateSelectedUI(ureq);
		}	

		if(selectMapLink != null) {
			selectMapLink.setVisible(copyMap != null || (copyBinder != null && copyBinder.getBinderStatus() != BinderStatus.deleted));
		}
		if(newMapLink != null) {
			newMapLink.setVisible(copyMap == null && (copyBinder == null || copyBinder.getBinderStatus() == BinderStatus.deleted));
		}
		if(newMapMsgEl != null) {
			newMapMsgEl.setVisible(copyMap == null && (copyBinder == null || copyBinder.getBinderStatus() == BinderStatus.deleted));
		}
	}
	
	private void updateEmptyUI() {
		String title = "";
		if(templateMap != null) {
			title = StringHelper.escapeHtml(templateMap.getTitle());
		} else if(templateBinder != null) {
			title = StringHelper.escapeHtml(templateBinder.getTitle());
		}

		String msg = translate("map.available", new String[]{ title });
		if(newMapMsgEl == null) {
			newMapMsgEl = uifactory.addStaticTextElement("map.available", msg, infosContainer);
		}
		newMapMsgEl.setLabel(null, null);
		
		FormLayoutContainer buttonGroupLayout = FormLayoutContainer.createButtonLayout("buttons", getTranslator());
		buttonGroupLayout.setRootForm(mainForm);
		infosContainer.add(buttonGroupLayout);
		if(newMapLink == null) {
			newMapLink = uifactory.addFormLink("map.new", buttonGroupLayout, Link.BUTTON);
			newMapLink.setElementCssClass("o_sel_ep_new_map_template");
		}
	}
	
	private void updateSelectedUI(UserRequest ureq) {
		if(selectMapLink == null) {
			selectMapLink = uifactory.addFormLink("select", "select.mymap", "select.mymap", infosContainer, Link.LINK);
			selectMapLink.setElementCssClass("o_sel_ep_select_map");
		} else {
			selectMapLink.setVisible(true);
		}
		
		if(copyMap != null) {
			updateSelectedMapUI(ureq);
		} else if(copyBinder != null) {
			updateSelectedBinderUI(ureq);
		}
	}

	private void updateSelectedBinderUI(UserRequest ureq) {
		String copyTitle = StringHelper.escapeHtml(copyBinder.getTitle());
		selectMapLink.getComponent().setCustomDisplayText(copyTitle);
		
		updateCopyDate(copyBinder.getCopyDate());
		updateAssessmentInfos(ureq, copyBinder.getReturnDate());
		updateDeadlineText(copyBinder.getDeadLine());
	}

	private void updateSelectedMapUI(UserRequest ureq) {	
		String copyTitle = StringHelper.escapeHtml(copyMap.getTitle());
		selectMapLink.getComponent().setCustomDisplayText(copyTitle);
		
		// show results, when already handed in
		EPStructuredMap structuredMap = (EPStructuredMap)copyMap;
		updateCopyDate(structuredMap.getCopyDate());
		updateAssessmentInfos(ureq, structuredMap.getReturnDate());
		updateDeadlineText(structuredMap.getDeadLine());
	}
	
	private void updateCopyDate(Date copyDate) {
		if(copyDate != null) {
			String copyDateStr = formatter.formatDateAndTime(copyDate);
			uifactory.addStaticTextElement("map.copyDate", copyDateStr, infosContainer);			
		}
	}
	
	/**
	 * Show absolute deadline when task is taken. nothing if taken map still has a deadline configured.
	 * @param deadline
	 */
	private void updateDeadlineText(Date deadlineDate) {
		if (deadlineDateText != null && deadlineDate != null) {
			String deadline = formatter.formatDateAndTime(deadlineDate);
			deadlineDateText.setValue(deadline);
			deadlineDateText.setLabel("map.deadline.absolut.label", null);
		}
	}
	
	private void updateAssessmentInfos(UserRequest ureq, Date returnDate) {
		if(userCourseEnv.isParticipant() && (returnDate != null || copyBinder != null)) {
			String rDate = formatter.formatDateAndTime(returnDate);
			uifactory.addStaticTextElement("map.returnDate", rDate, infosContainer);

			// Fetch all score and passed and calculate score accounting for the entire course
			ScoreAccounting scoreAccounting = userCourseEnv.getScoreAccounting();
			scoreAccounting.evaluateAll();			
			ScoreEvaluation scoreEval = scoreAccounting.evalCourseNode(courseNode);
			
			AssessmentConfig assessmentConfig = courseAssessmentService.getAssessmentConfig(courseNode);
			
			boolean resultsVisible = scoreEval.getUserVisible() == null || scoreEval.getUserVisible().booleanValue();
			assessmentInfosContainer.contextPut("resultsVisible", Boolean.valueOf(resultsVisible));
			//score
			Boolean hasScore = Boolean.valueOf(Mode.none != assessmentConfig.getScoreMode());
			Boolean hasPassed = Boolean.valueOf(Mode.none != assessmentConfig.getPassedMode());
			assessmentInfosContainer.contextPut("hasScoreField", hasScore);
			if(hasScore.booleanValue()) {
				Float score = scoreEval.getScore();
				Float minScore = assessmentConfig.getMinScore();
				Float maxScore = assessmentConfig.getMaxScore();
				assessmentInfosContainer.contextPut("scoreMin", AssessmentHelper.getRoundedScore(minScore));
				assessmentInfosContainer.contextPut("scoreMax", AssessmentHelper.getRoundedScore(maxScore));
				assessmentInfosContainer.contextPut("score", AssessmentHelper.getRoundedScore(score));
			}

			//passed
			assessmentInfosContainer.contextPut("hasPassedField", hasPassed);
			if(hasPassed.booleanValue()) {
				Boolean passed = scoreEval.getPassed();
				assessmentInfosContainer.contextPut("passed", passed);
				assessmentInfosContainer.contextPut("hasPassedValue", new Boolean(passed != null));
				Float cutValue = assessmentConfig.getCutValue();
				assessmentInfosContainer.contextPut("passedCutValue", AssessmentHelper.getRoundedScore(cutValue));
			}

			// get comment
			if(resultsVisible) {
				if(assessmentConfig.hasComment()) {
					AssessmentManager am = userCourseEnv.getCourseEnvironment().getAssessmentManager();
					String comment = am.getNodeComment(courseNode, getIdentity());
					assessmentInfosContainer.contextPut("comment", comment);
					assessmentInfosContainer.contextPut("incomment", isPanelOpen(ureq, "comment", true));
				}
				
				if(assessmentConfig.hasIndividualAsssessmentDocuments()) {
					List<File> docs = courseAssessmentService.getIndividualAssessmentDocuments(courseNode, userCourseEnv);
					String mapperUri = registerCacheableMapper(ureq, null, new DocumentsMapper(docs));
					assessmentInfosContainer.contextPut("docsMapperUri", mapperUri);
					assessmentInfosContainer.contextPut("docs", docs);
					assessmentInfosContainer.contextPut("inassessmentDocuments", isPanelOpen(ureq, "assessmentDocuments", true));
				}
			}
			assessmentInfosContainer.setVisible(true);
		} else {
			assessmentInfosContainer.setVisible(false);
		}
	}
	
	@Override
	protected void doDispose() {
		//
	}

	@Override
	protected void event(UserRequest ureq, Controller source, Event event) {
		if(restoreBinderCtrl == source) {
			if(DialogBoxUIFactory.isYesEvent(event)) {
				doRestore();
				updateUI(ureq);
			}
		}
		super.event(ureq, source, event);
	}

	@Override
	protected void formOK(UserRequest ureq) {
		//
	}

	@Override
	protected void formInnerEvent(UserRequest ureq, FormItem source, FormEvent event) {
		if(source == newMapLink) {
			RepositoryEntry courseEntry = userCourseEnv.getCourseEnvironment().getCourseGroupManager().getCourseEntry();
			Date deadline = courseNode.getDeadline();
			if(templateMap != null) {
				copyMap = ePFMgr.assignStructuredMapToUser(getIdentity(), templateMap, courseEntry, courseNode.getIdent(), null, deadline);
				if(copyMap != null) {
					showInfo("map.copied", StringHelper.escapeHtml(templateMap.getTitle()));
					ThreadLocalUserActivityLogger.addLoggingResourceInfo(LoggingResourceable.wrapPortfolioOres(copyMap));
					ThreadLocalUserActivityLogger.log(EPLoggingAction.EPORTFOLIO_TASK_STARTED, getClass());
				}
			} else if(templateBinder != null) {
				if(copyBinder == null) {
					copyBinder = portfolioService.assignBinder(getIdentity(), templateBinder, courseEntry, courseNode.getIdent(), deadline);
					if(copyBinder != null) {
						showInfo("map.copied", StringHelper.escapeHtml(templateBinder.getTitle()));
						ThreadLocalUserActivityLogger.addLoggingResourceInfo(LoggingResourceable.wrap(copyBinder));
						ThreadLocalUserActivityLogger.log(PortfolioLoggingAction.PORTFOLIO_TASK_STARTED, getClass());
					}
				} else if(copyBinder != null && copyBinder.getBinderStatus() == BinderStatus.deleted) {
					String title = translate("trashed.binder.confirm.title");
					String text = translate("trashed.binder.confirm.descr", new String[]{ StringHelper.escapeHtml(copyBinder.getTitle()) });
					restoreBinderCtrl = activateYesNoDialog(ureq, title, text, restoreBinderCtrl);
					restoreBinderCtrl.setUserObject(copyBinder);
					return;
				}
			}
			
			updateUI(ureq);
		} else if (source == selectMapLink) {
			String resourceUrl;
			if(copyMap != null) {
				resourceUrl = "[HomeSite:" + getIdentity().getKey() + "][Portfolio:0][EPStructuredMap:" + copyMap.getKey() + "]";
			} else if(copyBinder != null) {
				resourceUrl = "[HomeSite:" + getIdentity().getKey() + "][PortfolioV2:0][MyBinders:0][Binder:" + copyBinder.getKey() + "]";
			} else {
				return;
			}
			BusinessControl bc = BusinessControlFactory.getInstance().createFromString(resourceUrl);
			WindowControl bwControl = BusinessControlFactory.getInstance().createBusinessWindowControl(bc, getWindowControl());
			NewControllerFactory.getInstance().launch(ureq, bwControl);
		} else if("ONCLICK".equals(event.getCommand())) {
			String cmd = ureq.getParameter("fcid");
			String panelId = ureq.getParameter("panel");
			if(StringHelper.containsNonWhitespace(cmd) && StringHelper.containsNonWhitespace(panelId)) {
				saveOpenPanel(ureq, panelId, "show".equals(cmd));
			}
		}
	}
	
	private void doRestore() {
		copyBinder = portfolioService.getBinderByKey(copyBinder.getKey());
		copyBinder.setBinderStatus(BinderStatus.open);
		copyBinder = portfolioService.updateBinder(copyBinder);
		showInfo("restore.binder.success");
	}
	
	private boolean isPanelOpen(UserRequest ureq, String panelId, boolean def) {
		Preferences guiPrefs = ureq.getUserSession().getGuiPreferences();
		Boolean showConfig  = (Boolean) guiPrefs.get(PortfolioCourseNodeRunController.class, getOpenPanelId(panelId));
		return showConfig == null ? def : showConfig.booleanValue();
	}
	
	private void saveOpenPanel(UserRequest ureq, String panelId, boolean newValue) {
		Preferences guiPrefs = ureq.getUserSession().getGuiPreferences();
		if (guiPrefs != null) {
			guiPrefs.putAndSave(PortfolioCourseNodeRunController.class, getOpenPanelId(panelId), new Boolean(newValue));
		}
		flc.getFormItemComponent().contextPut("in-" + panelId, new Boolean(newValue));
	}
	
	private String getOpenPanelId(String panelId) {
		return panelId + "::" + userCourseEnv.getCourseEnvironment().getCourseResourceableId() + "::" + courseNode.getIdent();
	}
}
