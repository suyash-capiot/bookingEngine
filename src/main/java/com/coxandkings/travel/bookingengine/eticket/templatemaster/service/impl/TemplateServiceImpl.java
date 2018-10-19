package com.coxandkings.travel.bookingengine.eticket.templatemaster.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.assembler.TemplateAssembler;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.EmailObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.EmailToSend;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.TemplateInfo;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.TemplateRequestResource;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.response.TemplateResponse;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.TemplateService;
import com.itextpdf.text.Document;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.coxandkings.travel.bookingengine.config.ETicketConfig;
import java.util.Base64;

@Service
public class TemplateServiceImpl implements TemplateService {
	private static final Logger logger = Logger.getLogger(TemplateServiceImpl.class);

	private String processDefUrl = ETicketConfig.getmProcessDefUrl();

	private String dtmsUser = ETicketConfig.getmDtmsUser();

	private String dtmsPass = ETicketConfig.getmDtmsPass();

	private String userId = ETicketConfig.getmUserId();

	private String lookup = ETicketConfig.getmLookup();

	private String templateUrl = ETicketConfig.getmTemplateUrl();

	private String modeshapeUrl = ETicketConfig.getmModeshapeUrl();

	private String outIdentifier = ETicketConfig.getmOutIdentifier();

	private Boolean returnObject = false;

	private String entryPoint = ETicketConfig.getmEntryPoint();

	private String transactionId = ETicketConfig.getmTransactionId();

	private String mailUrl = ETicketConfig.getmMailUrl();

	private String todopostUrl = ETicketConfig.getmTodopostUrl();

	private String todogetUrl = ETicketConfig.getmTodogetUrl();

	private String userdir = ETicketConfig.getmUserdir();

	private String fileseparator = ETicketConfig.getmFileseparator();

	@Override
	public String getTemplate(TemplateInfo templateInfo, List<DynamicAttributes> dynamicValues, TemplateTypes type,
			FileType filetype, String uniqueId) {
		logger.info("In getTemplate() method");
		return getContent(templateInfo, dynamicValues, type, filetype, uniqueId);
	}

	private String getContent(TemplateInfo templateInfo, List<DynamicAttributes> dynamicValues, TemplateTypes type,
			FileType fileType, String uniqueId) {

		logger.info("In getContent() method");
		TemplateRequestResource request = new TemplateRequestResource(templateInfo, dynamicValues, lookup,
				outIdentifier, returnObject, entryPoint, userId, transactionId);
		JSONObject requestjson = new JSONObject(request);
		logger.info("Final Request for DTMS to replace the template values : " + requestjson);

		RestTemplate template = new RestTemplate();
		HttpEntity<TemplateRequestResource> httpEntity = new HttpEntity<TemplateRequestResource>(request,
				createHeaders(dtmsUser, dtmsPass));

		ResponseEntity<TemplateResponse> responseEntity = template.exchange(this.processDefUrl, HttpMethod.POST,
				httpEntity, TemplateResponse.class);

		TemplateResponse templateResponse = responseEntity.getBody();

		JSONObject templateresponsejson = new JSONObject(templateResponse);
		logger.info("Template Response after replacing dynamic values : " + templateresponsejson);

		String toReturn = templateResponse.getResult().getExecutionResults().getResults().get(0).getValue().get(0)
				.getDocumentTemplateManagement().getDtmOutput().getTemplates().stream()
				.filter(templatehere -> templatehere.getTemplateType().equalsIgnoreCase(type.toString())).findFirst()
				.get().getDynamicTemplateContent();
		toReturn = toReturn.replace('\"', '"');
		System.getProperty("user.dir");
		logger.info("paragraph content in toReturn variable : " + toReturn);
		// DMS Module
		if (type != TemplateTypes.Email) {
			/*
			 * File toSend = convertToPdf(toReturn, fileType.toString(), uniqueId); File
			 * fileWithNewName = new File(toSend.getParent(), uniqueId + ".pdf");
			 * toSend.renameTo(fileWithNewName);
			 * logger.info("File Rename with uniqueID : "+fileWithNewName);
			 */
			/*
			 * ResponseEntity<String> entity = null; UriComponentsBuilder builder =
			 * UriComponentsBuilder.fromHttpUrl(modeshapeUrl); builder.queryParam("type",
			 * "pdf"); builder.queryParam("name", uniqueId); builder.queryParam("category",
			 * fileType.toString()); LinkedMultiValueMap<String, Object> params = new
			 * LinkedMultiValueMap<>(); params.add("file", new
			 * FileSystemResource(fileWithNewName)); HttpHeaders headers = new
			 * HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			 */
			// MDMDataSource data = new MDMDataSource();
			/*
			 * try { headers.set("Authorization", data.getToken().getToken()); } catch
			 * (FinanceException e) { e.printStackTrace(); }
			 */
			/*
			 * HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new
			 * HttpEntity<>(params, headers); try { entity =
			 * template.exchange(builder.build().encode().toUriString(), HttpMethod.POST,
			 * requestEntity, String.class); }
			 * 
			 * catch (Exception e1) { e1.printStackTrace(); } finally { //
			 * fileWithNewName.delete(); } String uid = entity.getBody();
			 * 
			 * return uid;
			 */
		}
		return toReturn;
	}

	public File convertToPdf(String toReturn, String fileName, String uniqueId) {
		try {
			Document document = new Document(new Rectangle(0, 0, 1000, 900), 25f, 25f, 25f, 25f);
			// String workingDirectory = System.getProperty("user.dir");
			String workingDirectory = userdir;
			// File file = new File(workingDirectory + System.getProperty("file.separator")
			// + "PdfFile.pdf");
			File file = new File(workingDirectory + fileseparator + "PdfFile.pdf");
			logger.info("File path to create pdf : " + file);
			PdfWriter pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(file));
			document.open();
			XMLWorkerHelper worker = XMLWorkerHelper.getInstance();
			Long startTime = System.currentTimeMillis();
			worker.parseXHtml(pdfWriter, document, new StringReader(toReturn));
			Long endTime = System.currentTimeMillis();
			document.close();
			System.out.println(endTime - startTime);
			logger.info("PDF file generated Successfully");
			return file;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("serial")
	private static HttpHeaders createHeaders(String username, String password) {
		try {
			return new HttpHeaders() {
				{
					String auth = username + ":" + password;
					// byte[] encodedAuth =
					// Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));

					byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes("utf-8"));
					String authHeader = "Basic " + new String(encodedAuth);
					set("Authorization", authHeader);
					setContentType(MediaType.APPLICATION_JSON);
				}
			};
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String sendEmail(EmailObject emailObject) {
		String val = null;
		try {
			EmailToSend emailToSend = null;
			emailToSend = TemplateAssembler.convertEmail(emailObject, emailToSend);
			RestTemplate template = new RestTemplate();
			HttpHeaders headers = new HttpHeaders();
			headers.set("Content-Type", "application/json");
			HttpEntity<EmailToSend> httpEntity = new HttpEntity<EmailToSend>(emailToSend, headers);
			val = template.exchange(mailUrl, HttpMethod.POST, httpEntity, String.class).getBody();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return val;
	}

	@Override
	public String postTempalte(MainObject main) {
		logger.info("In postTemplate() method");
		List<DynamicAttributes> dynamicVariables = main.getDynamicVariables();
		TemplateTypes templateTypes = main.getTemplateTypes();
		FileType fileType = main.getFileType();
		String uniqueId = main.getUniqueId();
		String companyName = main.getCompanyName();
		String token = main.getToken();
		// String functionalArea = "Finance";
		String functionalArea = "BookingEngine";
		String templateHtml = null;
		logger.info(String.format(
				"Values for getTemplateInfo() methods are: Functional area:- %s FileType:- %s Company Name:- %s ",
				functionalArea, fileType, companyName, token));
		try {
			TemplateInfo templateInfo = getTemplateInfo(functionalArea, fileType, companyName, token);
			templateHtml = getTemplate(templateInfo, dynamicVariables, templateTypes, fileType, uniqueId);
		} catch (Exception e) {

			// throw new
			// IllegalIdentifierException(ErrorMessageFactory.createMessageWhenTemplateException());
		}
		System.out.println("templateHtml   " + templateHtml);
		return templateHtml;

	}

	@Override
	public TemplateInfo getTemplateInfo(String functionalArea, FileType fileType, String companyName, String token) {
		logger.info("In getTemplateInfo() method");
		Map<String, String> newMap = new HashMap<>();
		newMap.put("applicability.businessProcess", functionalArea);
		newMap.put("applicability.company.name", companyName);
		newMap.put("applicability.function", fileType.toString());
		JSONObject jsonObject = new JSONObject(newMap);

		ResponseEntity<String> responseEntity = null;
		RestTemplate restTemplate = new RestTemplate();
		TemplateInfo info = new TemplateInfo();

		try {

			String url = templateUrl;
			logger.info("Template URL : " + url);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("Authorization", token);
			// headers.set("Authorization", token);
			HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
			logger.info("Json applicability Request for calling particular Template " + jsonObject);
			responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class, jsonObject);
			JSONObject json = new JSONObject(responseEntity.getBody());
			logger.info("Template response from MDM for particular Template " + json);
			info = TemplateAssembler.convertToTemplate(json);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return info;
	}

}
