package de.bps.webservices.clients.onyxreporter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.olat.core.id.Identity;
import org.olat.core.id.OLATResourceable;
import org.olat.core.id.UserConstants;
import org.olat.core.logging.OLog;
import org.olat.core.logging.Tracing;
import org.olat.core.util.WebappHelper;
import org.olat.course.nodes.AssessableCourseNode;
import org.olat.course.nodes.CourseNode;
import org.olat.fileresource.FileResourceManager;
import org.olat.repository.RepositoryEntry;

import de.bps.onyx.plugin.OnyxResultManager;
import de.bps.onyx.plugin.course.nodes.iq.IQEditController;
import de.bps.security.SSLConfigurationModule;

//<ONYX-705>
public class OnyxReporterConnector {
	//<OLATCE-1124>
	private static final String FOUR = "4";
	private static final String ONE = "1";
	private static final String FIVE = "5";
	//</OLATCE-1124>
	//<OLATCE-1089>
	private static final String NONAME = "NONAME";
	//</OLATCE-1089>
	private String surveyFolderPath;
	
	private OLog log = Tracing.createLoggerFor(OnyxReporterConnector.class);
	private final OnyxReporterClient connector;
	
	
	public OnyxReporterConnector() throws OnyxReporterException {
		
		//TODO check if available
		if(isServiceAvailable(OnyxReporterTarget.getTarget())) {
			connector = new OnyxReporterClient();
		} else {
			//<OLATCE-1124>
			log.error("OnyxReporterService is unavailable! Tried to use: "+OnyxReporterTarget.getTarget());
			//</OLATCE-1124>
			throw new OnyxReporterException("Unable to connect to OnyxReporter");
		}
		
	}
	
	
	/**
	 * Delivers a map with all possible outcome-variables of this onyx test.
	 * @param node The course node with the Onyx test.
	 * @return A map with all outcome-variables with name as key and type as value.
	 */
	//<OLATCE-1012> split the method
	// <OLATBPS-363>
	public Map<String, String> getPossibleOutcomeVariables(CourseNode node) throws OnyxReporterException {
	// </OLATBPS-363>
		RepositoryEntry repositoryEntry = node.getReferencedRepositoryEntry();
		return this.getPossibleOutcomeVariables(repositoryEntry);
	}
	//<OLATCE-1012>
	
	//<OLATCE-1012>
	/**
	 * Delivers a map with all possible outcome-variables of this onyx test.
	 * @param  Repoentry with the Onyx test.
	 * @return A map with all outcome-variables with name as key and type as value.
	 */
	public Map<String, String> getPossibleOutcomeVariables(RepositoryEntry entry) throws OnyxReporterException {
		OnyxReporterServices reporterService = connector.getService();
		
		HashMapWrapper resultVariables = reporterService.getResultVariables(1, getContentPackage(entry), new HashMapWrapper());
		HashMap<String, String> results;
		try {
			results = resultVariables.getMap();
		} catch (OnyxReporterException e) {
			log.error("Error in getPossibleOutcomeVariables reporter conversation! RepositoryEntry: " + entry.getResourceableId(), e);
			results = new HashMap<String, String>();
		}

		return results;
	}
	//<OLATCE-1012>	
	

	
	// <OLATBPS-363>
	public Map<String, String> getResults(File resultXml, CourseNode node) throws OnyxReporterException {
	// </OLATBPS-363>
		return getResults(resultXml, node, null);
	}

	public Map<String, String> getResults(AssessableCourseNode node, Identity identity) throws OnyxReporterException {
		//<OLATCE-1073>
		File resultXml = getResultXml(identity.getName(), node.getModuleConfiguration().get(IQEditController.CONFIG_KEY_TYPE).toString(), node.getIdent(), 0);
		return getResults(resultXml, node, identity);
		//</OLATCE-1073>
	}
	// <OLATBPS-363>
	public Map<String, String> getResults(File resultXml, CourseNode node, Identity identity) throws OnyxReporterException {
	// </OLATBPS-363>
		//<OLATCE-1073>
		if(resultXml == null){
			log.info("Missing resultFile! For "+(identity!=null?identity.getName():"NULL")+" node : "+(node!=null?(node.getShortName()+":"+node.getIdent()):"NULL"));
			return new HashMap<String, String>(0);
		}
		//</OLATCE-1073>
		
		RepositoryEntry repositoryEntry = node.getReferencedRepositoryEntry();
		OnyxReporterServices reporterService = connector.getService();
		
		/** ARM SITE **/
		String[] dlh = armSite(reporterService, identity, false);
		
		String secret = dlh[0];
		String sessionId = dlh[1];

		/** Prepare data **/
		ArrayList<ResultsForStudent> resForStudents = new ArrayList<ResultsForStudent>(4);
		
		resForStudents.add(getStudentWithResult(identity, resultXml));
		
		ResultsForStudentsWrapper wrapper = new ResultsForStudentsWrapper();
		
		wrapper.setStudents(resForStudents);
		
		/** INITIATE SITE **/
		reporterService.initiateSite(1, sessionId, secret, wrapper, getContentPackage(repositoryEntry), new HashMapWrapper());
		
		
		HashMapWrapper mapWrapper = reporterService.getResultValues(1, sessionId, secret, new HashMapWrapper(), new HashMapWrapper());
		Map<String, String> results;
		try {
			results = mapWrapper.getMap();
		} catch (OnyxReporterException e) {
			log.error("Error in getResults reporter conversation! Session: " + sessionId + ", Identity: " + identity.getName(), e);
			throw new OnyxReporterException("Error getting results for test! Session: " + sessionId, e);
		}
		
		return results;
	}
	
	/**
	 * This method starts the OnyxReporter and returns the link to it.
	 * @param students The students to show the results for.
	 * @param node The AssessableCourseNode to get the nodeId and to get the (OnyxTest) RepositoryEntry.
	 * @param studentview True if OnyxReporter shall display the detail view of the one given student,
	 * 				false if it shall display the overall results for all given students.
	 * @param reporterView TODO
	 * @param ureq The UserRequest for getting the identity and role of the current user.
	 * @return the Link to the reporter.
	 */
	//<OLATCE-1124>
	public String startReporterGUI(Identity caller, List<Identity> students, CourseNode node, Long assessmentId, boolean studentview, boolean reporterView) throws OnyxReporterException{
	//</OLATCE-1124>
		String link = "";
		RepositoryEntry repositoryEntry = node.getReferencedRepositoryEntry();
				
		ArrayList<ResultsForStudent> resForStudents = null;
		if(surveyFolderPath == null){
			resForStudents = getStudentsWithResults(students, node, assessmentId);
		} else {
			resForStudents = getAnonymizedStudentsWithResultsForSurvey(node.getIdent());
		}
		
		try {
			OnyxReporterServices reporterService = connector.getService();
			
			String[] dlh = armSite(reporterService, caller, studentview);
			
			byte[] contentPackage = getContentPackage(repositoryEntry);
			
			ResultsForStudentsWrapper wrapper = new ResultsForStudentsWrapper();
			wrapper.setStudents(resForStudents);
			
			link = reporterService.initiateSite(1, dlh[1], dlh[0], wrapper, contentPackage, new HashMapWrapper());
			
			if (link == null) {
				throw new OnyxReporterException("Unable to start ReporterGUI! Could not resolve reporter URL!");
			} else if (link.indexOf("reportererror") >= 0) {
				// use error link to show reporter error page
			} else {
				//<OLATCE-1124>
				if (reporterView) {
					link += FIVE; // view 5 for reporting view / statistical evaluation
				} else 	if (studentview) {
					link += ONE; // view 1 (single learner view)
				} else {
					link += FOUR; // view 4 (all learners overview)
				}
				//</OLATCE-1124>
				
				//add params
				link += "?sid=" + dlh[1] + "&secret=" + dlh[0];
				
				//switch to the student view of a specified student
				if (studentview) {
					link += "&uid="+ students.get(0).getKey();
				}
			}
		} catch (Exception e) {
			throw new OnyxReporterException("Unable to start ReporterGUI!", e);
		}
		
		return link;
	}
	
	// <OLATCE-498>
	public boolean hasResultXml(String username, String assessmentType, String nodeId) {
		return getResultXml(username, assessmentType, nodeId, 0) != null;
	}
	// </OLATCE-498>
	
	
	public String startReporterGUIForSurvey(Identity caller, CourseNode node, String resultsPath) throws OnyxReporterException{
		this.surveyFolderPath = resultsPath;
		//<OLATCE-1124>
		return startReporterGUI(caller, null, node, null, false, true);
		//</OLATCE-1124>
	}
	
	
	
	
	private byte[] getContentPackage(RepositoryEntry repositoryEntry){
		File cpFile = FileResourceManager.getInstance().getFileResource(repositoryEntry.getOlatResource());
		
		if(cpFile==null || !cpFile.exists()){
			cpFile = getCP(repositoryEntry);
		}
		
		Long fileLength = cpFile.length();
		byte[] contentPackage = new byte[fileLength.intValue()];
		
		try {
			java.io.FileInputStream inp;
			inp = new java.io.FileInputStream(cpFile);
			inp.read(contentPackage);
		} catch (FileNotFoundException e) {
			log.error("Missing file: "+cpFile.getAbsolutePath(),e);
		} catch (IOException e) {
			log.error("Error copying file: "+cpFile.getAbsolutePath(),e);
		}
		
		return contentPackage;
	}
	
	private String[] armSite(OnyxReporterServices reporterService, Identity caller, boolean studentview){

		String secret = "" + new Random().nextLong();
		
		//<OLATCE-1089>
		String lastname = caller.getUser().getProperty(UserConstants.LASTNAME, null);
		String firstname = caller.getUser().getProperty(UserConstants.FIRSTNAME,null);
		lastname=lastname!=null&&lastname.length()>0?lastname:NONAME;
		firstname=firstname!=null&&firstname.length()>0?firstname:NONAME;
		
		String reporterSessionId = reporterService.armSite(1, caller.getName(), studentview?0:1 , secret,
				lastname, firstname, new HashMapWrapper());
		//</OLATCE-1089>
		return new String[]{secret, reporterSessionId!=null?reporterSessionId:"dummy"};
	}
	
	
	
	private ResultsForStudent getStudentWithResult(Identity student, File resultFile){
		ResultsForStudent resForStudent = null;
		Long fileLength = resultFile.length();
		byte[] resultFileStream = new byte[fileLength.intValue()];
		java.io.FileInputStream inp;
		
		try {
			inp = new java.io.FileInputStream(resultFile);
			inp.read(resultFileStream);

			//<OLATCE-1089>
			String lastname = student.getUser().getProperty(UserConstants.LASTNAME, null);
			String firstname = student.getUser().getProperty(UserConstants.FIRSTNAME,null);
			lastname=lastname!=null&&lastname.length()>0?lastname:NONAME;
			firstname=firstname!=null&&firstname.length()>0?firstname:NONAME;
			
			resForStudent = new ResultsForStudent();
			//<OLATCE-1169>
			resForStudent.setFirstname(firstname);
			resForStudent.setLastname(lastname);
			//</OLATCE-1169>
			//</OLATCE-1089>
			resForStudent.setStudentId(String.valueOf(student.getKey()));
			resForStudent.setGroupname("");
			resForStudent.setTutorname("");
			resForStudent.setResultsFile(resultFileStream);
			
			
		} catch (FileNotFoundException e) {
			log.error("Missing file: "+resultFile.getAbsolutePath(),e);
		} catch (IOException e) {
			log.error("Error copying file: "+resultFile.getAbsolutePath(),e);
		}
		return resForStudent;
		
	}
	
	
	private ArrayList<ResultsForStudent> getStudentsWithResults(List<Identity> students, CourseNode node, Long assessmentId){
		ArrayList<ResultsForStudent> resForStudents = new ArrayList<ResultsForStudent>();
		
		for(Identity student : students){
			File resultFile = getResultXml(student.getName(), node.getModuleConfiguration().get(IQEditController.CONFIG_KEY_TYPE).toString(), node.getIdent(), assessmentId!=null?assessmentId:0);
			//<OLATCE-1048>
			if(resultFile != null)
				resForStudents.add(getStudentWithResult(student, resultFile));
			//</OLATCE-1048>
		}
		
		return resForStudents;
	}
	
	
	/**
	 * For every result xml file found in the survey folder a dummy student is created.
	 * @param nodeId
	 * @return
	 */
	private ArrayList<ResultsForStudent> getAnonymizedStudentsWithResultsForSurvey(String nodeId) {
		ArrayList<ResultsForStudent> serviceStudents = new ArrayList<ResultsForStudent>();

		File directory = new File(this.surveyFolderPath);
		Long fileLength;
		File resultFile;
		if(directory.exists()) {
			String[] allXmls = directory.list(new myFilenameFilter(nodeId));
			if (allXmls != null && allXmls.length > 0) {
				int id = 0;
				for (String xmlFileName : allXmls) {
					
					ResultsForStudent serviceStudent = new ResultsForStudent();
					serviceStudent.setFirstname("");
					serviceStudent.setLastname("");
					serviceStudent.setGroupname("");
					serviceStudent.setTutorname("");
					serviceStudent.setStudentId("st" + id);
					
					resultFile = new File(this.surveyFolderPath + xmlFileName);
					fileLength = resultFile.length();
					byte[] resultFileStream = new byte[fileLength.intValue()];
					java.io.FileInputStream inp;
					
					try {
						inp = new java.io.FileInputStream(resultFile);
						inp.read(resultFileStream);
					
					serviceStudent.setResultsFile(resultFileStream);
					
					
					serviceStudents.add(serviceStudent);
					id++;
					} catch (FileNotFoundException e) {
						log.error("Missing file: "+resultFile.getAbsolutePath(),e);
					} catch (IOException e) {
						log.error("Error copying file: "+resultFile.getAbsolutePath(),e);
					}
				}
			}
		}
		return serviceStudents;
	}
	
	
	
	private File getResultXml(String username, String assessmentType, String nodeId, long assessmentId) {
		File xml = null;
		String filename;
		File fUserdataRoot = new File(WebappHelper.getUserDataRoot());
		String path = OnyxResultManager.getResReporting() + File.separator + username + File.separator
		+ assessmentType + File.separator;
		//if an assassment id was given, use the corresponding file
		if (assessmentId != 0) {
			filename = nodeId + "v" + assessmentId + ".xml";
			xml  = new File(fUserdataRoot, path + filename);
		}
		//otherwise search the newest result file with this node id in this directory
		if (xml == null || !(xml.exists())) {
			File directory = new File(fUserdataRoot, path);
			String[] allXmls = directory.list(new myFilenameFilter(nodeId));
			if (allXmls != null && allXmls.length > 0) {
				File newestXml = new File(fUserdataRoot, path + allXmls[0]);
				for (String xmlFileName : allXmls) {
					File xmlFile = new File(fUserdataRoot, path + xmlFileName);
					if (xmlFile.lastModified() > newestXml.lastModified()) {
						newestXml = xmlFile;
					}
				}
				xml = newestXml;
			}
		}
		
		if (xml == null || !(xml.exists())) {
			//<OLATCE-1048>
			xml = null;
			//</OLATCE-1048>
			log.error("There is no file for this test and student "+username+" assessmentType: "+assessmentType+ " nodeId: "+nodeId+" assessmentId: "+assessmentId);
		}

		return xml;
	}
	
	
	
	
	/**
	 * Generates a file object for the given re.
	 * @param repositoryEntry
	 * @return
	 */
	private File getCP(RepositoryEntry repositoryEntry) {
		//get content-package (= onyx test zip-file)
		OLATResourceable fileResource = repositoryEntry.getOlatResource();
		String unzipedDir = FileResourceManager.getInstance().unzipFileResource(fileResource).getAbsolutePath();
		String zipdirName = FileResourceManager.ZIPDIR;
		String testName = repositoryEntry.getResourcename();
		String pathToFile = unzipedDir.substring(0, unzipedDir.indexOf(zipdirName));
		File onyxTestZip = new File(pathToFile + testName);
		// <OLATCE-499>
		if (!onyxTestZip.exists()) {
			onyxTestZip = new File(pathToFile + "repo.zip");
		}
		// </OLATCE-499>
		return onyxTestZip;
	}
	
	/**
	 * Description:<br>
	 * Filters the filenames of the "File.list()" method so that only files
	 * witch passes the method "accept" are returned.
	 *
	 * <P>
	 * Initial Date:  25.09.2009 <br>
	 * @author thomasw@bps-system.de
	 */
	private class myFilenameFilter implements FilenameFilter {

		private String nodeId;

		public myFilenameFilter(String nodeId) {
			this.nodeId = nodeId;
		}
		/**
		 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
		 */
		public boolean accept(File diretory, String name) {
			if (name.startsWith(nodeId)) {
				return true;
			} else {
				return false;
			}
		}
	}
	
	private boolean isServiceAvailable(String target) {

		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String urlHostName, SSLSession session) {
				if (urlHostName.equals(session.getPeerHost()))
					return true;
				else
					return false;
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hv);

		try {
			URL url = new URL(target + "?wsdl");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			if (con instanceof HttpsURLConnection) {
				HttpsURLConnection sslconn = (HttpsURLConnection) con;
				SSLContext context = SSLContext.getInstance("SSL");
				context.init(SSLConfigurationModule.getKeyManagers(),
						SSLConfigurationModule.getTrustManagers(),
						new java.security.SecureRandom());
				sslconn.setSSLSocketFactory(context.getSocketFactory());
				sslconn.connect();
				if (sslconn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					sslconn.disconnect();
					return true;
				}
			} else {
				con.connect();
				if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
					con.disconnect();
					return true;
				}
			}
		} catch (Exception e) {
			Tracing.createLoggerFor(getClass()).error("Error while trying to connect to webservice: " + target, e);
		}
		return false;
	}
	
	//<OLATCE-1124>
	public boolean hasAnyResults(boolean forSurvey,List<Identity> forStudents, String surveyFolder, CourseNode node){
		boolean hasResults = false;
		
		File directory = new File(WebappHelper.getUserDataRoot());
		if(forSurvey){
			File surveyDir = new File(surveyFolder);
			if(surveyDir.exists()) {
				
				String[] allXmls = surveyDir.list(new myFilenameFilter(node.getIdent()));
				if (allXmls != null && allXmls.length > 0) {
					hasResults = true;
				}
			}
		} else {
			String assessmentType = node.getModuleConfiguration().get(IQEditController.CONFIG_KEY_TYPE).toString();
			String path = null;
			for(Identity student : forStudents){
				path = OnyxResultManager.getResReporting() + File.separator + student.getName() + File.separator
				+ assessmentType + File.separator; 
				File	xml  = new File(directory, path);
				
				if (xml != null && xml.exists()) {
					String[] allXmls = xml.list(new myFilenameFilter(node.getIdent()));
					if (allXmls != null && allXmls.length > 0) {
						hasResults = true;
						break;
					}
				}	
				
			}
		}
		
		return hasResults;
	}
	//</OLATCE-1124>
}
//</ONYX-705>