package fr.paris.lutece.plugins.appointment.web;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import fr.paris.lutece.plugins.appointment.business.AppointmentDTO;
import fr.paris.lutece.plugins.appointment.business.AppointmentForm;
import fr.paris.lutece.plugins.appointment.business.ResponseRecapDTO;
import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.business.user.User;
import fr.paris.lutece.plugins.appointment.service.AppointmentResponseService;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.EntryService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.UserService;
import fr.paris.lutece.plugins.appointment.service.Utilities;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryFilter;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.Field;
import fr.paris.lutece.plugins.genericattributes.business.FieldHome;
import fr.paris.lutece.plugins.genericattributes.business.GenericAttributeError;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.business.ResponseHome;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.service.state.StateService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.util.beanvalidation.BeanValidationUtil;

/**
 * Utility class for Appointment Mutualize methods between MVCApplication and
 * MVCAdminJspBean
 * 
 * @author Laurent Payen
 *
 */
public class AppointmentUtilities {

	private static final String ERROR_MESSAGE_EMPTY_CONFIRM_EMAIL = "appointment.validation.appointment.EmailConfirmation.email";
	private static final String ERROR_MESSAGE_CONFIRM_EMAIL = "appointment.message.error.confirmEmail";
	private static final String ERROR_MESSAGE_EMPTY_EMAIL = "appointment.validation.appointment.Email.notEmpty";
	private static final String ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT = "appointment.validation.appointment.NbBookedSeat.notEmpty";
	private static final String ERROR_MESSAGE_ERROR_NB_BOOKED_SEAT = "appointment.validation.appointment.NbBookedSeat.error";

	private static final String KEY_RESOURCE_TYPE = "appointment.permission.label.resourceType";
	private static final String KEY_COLUMN_LAST_NAME = "appointment.manage_appointments.columnLastName";
	private static final String KEY_COLUMN_FISRT_NAME = "appointment.manage_appointments.columnFirstName";
	private static final String KEY_COLUMN_EMAIL = "appointment.manage_appointments.columnEmail";
	private static final String KEY_COLUMN_DATE_APPOINTMENT = "appointment.manage_appointments.columnDateAppointment";
	private static final String KEY_TIME_START = "appointment.model.entity.appointmentform.attribute.timeStart";
	private static final String KEY_TIME_END = "appointment.model.entity.appointmentform.attribute.timeEnd";
	private static final String KEY_COLUMN_STATUS = "appointment.manage_appointments.columnStatus";
	private static final String KEY_COLUM_LOGIN = "appointment.manage_appointments.columnLogin";
	private static final String KEY_COLUMN_STATE = "appointment.manage_appointments.columnState";
	private static final String KEY_COLUMN_NB_BOOKED_SEATS = "appointment.manage_appointments.columnNumberOfBookedseatsPerAppointment";

	private static final String CONSTANT_COMMA = ",";
	private static final String EXCEL_FILE_EXTENSION = ".xlsx";
	private static final String EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	/**
	 * Check that the email is correct and matches the confirm email
	 * 
	 * @param strEmail
	 *            the email
	 * @param strConfirmEmail
	 *            the confirm email
	 * @param form
	 *            the form
	 * @param locale
	 *            the local
	 * @param listFormErrors
	 *            the list of errors that can be fill in with the errors found
	 *            for the email
	 */
	public static void checkEmail(String strEmail, String strConfirmEmail, AppointmentForm form, Locale locale,
			List<GenericAttributeError> listFormErrors) {
		if (form.getEnableMandatoryEmail()) {
			if (StringUtils.isEmpty(strEmail)) {
				GenericAttributeError genAttError = new GenericAttributeError();
				genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_EMPTY_EMAIL, locale));
				listFormErrors.add(genAttError);
			}
			if (StringUtils.isEmpty(strConfirmEmail)) {
				GenericAttributeError genAttError = new GenericAttributeError();
				genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_EMPTY_CONFIRM_EMAIL, locale));
				listFormErrors.add(genAttError);
			}
		}
		if (!StringUtils.equals(strEmail, strConfirmEmail)) {
			GenericAttributeError genAttError = new GenericAttributeError();
			genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_CONFIRM_EMAIL, locale));
			listFormErrors.add(genAttError);
		}
	}

	/**
	 * Check that the user has no previous appointment or that the previous
	 * appointment respect the delay
	 * 
	 * @param dateOfTheAppointment
	 *            date of the new appointment
	 * @param strEmail
	 *            the email of the user
	 * @param form
	 *            the form
	 * @param locale
	 *            the locale
	 * @param listFormErrors
	 *            the list of errors that can be fill in with the errors found
	 * @return
	 */
	public static boolean checkUserAndAppointment(AppointmentDTO appointmentDTO, String strEmail,
			AppointmentForm form) {
		boolean bCheckPassed = true;
		if (StringUtils.isNotEmpty(strEmail)) {
			int nbDaysBetweenTwoAppointments = form.getNbDaysBeforeNewAppointment();
			if (nbDaysBetweenTwoAppointments != 0) {
				// Looking for existing user with this email
				User user = UserService.findUserByEmail(strEmail);
				if (user != null) {
					// looking for its appointment
					List<Appointment> listAppointment = AppointmentService
							.findListAppointmentByUserId(user.getIdUser());
					// If we modify an appointment, we remove the
					// appointment that we currently edit
					if (appointmentDTO.getIdAppointment() != 0) {
						listAppointment = listAppointment.stream()
								.filter(a -> a.getIdAppointment() != appointmentDTO.getIdAppointment())
								.collect(Collectors.toList());
					}
					if (CollectionUtils.isNotEmpty(listAppointment)) {
						// I know we could have a join sql query, but I don't
						// want to join the appointment table with the slot
						// table, it's too big and not efficient
						List<Slot> listSlot = new ArrayList<>();
						for (Appointment appointment : listAppointment) {
							listSlot.add(SlotService.findSlotById(appointment.getIdSlot()));
						}
						// Get the last appointment date for this form
						LocalDate dateOfTheLastAppointment = listSlot.stream()
								.filter(s -> s.getIdForm() == form.getIdForm()).map(Slot::getStartingDateTime)
								.max(LocalDateTime::compareTo).get().toLocalDate();
						// Check the number of days between this appointment and
						// the last appointment the user has taken
						LocalDate dateOfTheAppointment = appointmentDTO.getSlot().getStartingDateTime().toLocalDate();
						if ((dateOfTheLastAppointment.isBefore(dateOfTheAppointment)
								|| dateOfTheLastAppointment.equals(dateOfTheAppointment))
								&& dateOfTheLastAppointment.until(dateOfTheAppointment,
										ChronoUnit.DAYS) <= nbDaysBetweenTwoAppointments) {
							bCheckPassed = false;
						}
					}
				}
			}
		}
		return bCheckPassed;
	}

	/**
	 * Check and validate all the rules for the number of booked seats asked
	 * 
	 * @param strNbBookedSeats
	 *            the number of booked seats
	 * @param form
	 *            the form
	 * @param nbRemainingPlaces
	 *            the number of remaining places on the slot asked
	 * @param locale
	 *            the locale
	 * @param listFormErrors
	 *            the list of errors that can be fill in with the errors found
	 *            for the number of booked seats
	 * @return
	 */
	public static int checkAndReturnNbBookedSeats(String strNbBookedSeats, AppointmentForm form,
			AppointmentDTO appointmentDTO, Locale locale, List<GenericAttributeError> listFormErrors) {
		int nbBookedSeats = 1;
		if (StringUtils.isEmpty(strNbBookedSeats) && form.getMaxPeoplePerAppointment() > 1) {
			GenericAttributeError genAttError = new GenericAttributeError();
			genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT, locale));
			listFormErrors.add(genAttError);
		}
		if (StringUtils.isNotEmpty(strNbBookedSeats)) {
			nbBookedSeats = Integer.parseInt(strNbBookedSeats);
		}
		// if it's a new appointment, need to check if the number of booked
		// seats is under or equal to the number of remaining places
		// if it's a modification, need to check if the new number of booked
		// seats is under or equal to the number of the remaining places + the
		// previous number of booked seats of the appointment
		if ((appointmentDTO.getIdAppointment() == 0 && nbBookedSeats > appointmentDTO.getSlot().getNbRemainingPlaces())
				|| (appointmentDTO.getIdAppointment() != 0
						&& nbBookedSeats > (appointmentDTO.getSlot().getNbRemainingPlaces()
								+ appointmentDTO.getNbBookedSeats()))) {
			GenericAttributeError genAttError = new GenericAttributeError();
			genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_ERROR_NB_BOOKED_SEAT, locale));
			listFormErrors.add(genAttError);
		}

		if (nbBookedSeats == 0) {
			GenericAttributeError genAttError = new GenericAttributeError();
			genAttError.setErrorMessage(I18nService.getLocalizedString(ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT, locale));
			listFormErrors.add(genAttError);
		}
		return nbBookedSeats;
	}

	/**
	 * Fill the appoinmentFront DTO with the given parameters
	 * 
	 * @param appointmentDTO
	 *            the appointmentFront DTO
	 * @param nbBookedSeats
	 *            the number of booked seats
	 * @param strEmail
	 *            the email of the user
	 * @param strFirstName
	 *            the first name of the user
	 * @param strLastName
	 *            the last name of the user
	 */
	public static void fillAppointmentDTO(AppointmentDTO appointmentDTO, int nbBookedSeats, String strEmail,
			String strFirstName, String strLastName) {
		appointmentDTO.setDateOfTheAppointment(appointmentDTO.getSlot().getDate().format(Utilities.formatter));
		appointmentDTO.setNbBookedSeats(nbBookedSeats);
		appointmentDTO.setEmail(strEmail);
		appointmentDTO.setFirstName(strFirstName);
		appointmentDTO.setLastName(strLastName);
		Map<Integer, List<Response>> mapResponses = appointmentDTO.getMapResponsesByIdEntry();
		if (mapResponses != null) {
			List<Response> listResponse = new ArrayList<Response>();
			for (List<Response> listResponseByEntry : mapResponses.values()) {
				listResponse.addAll(listResponseByEntry);
			}
			appointmentDTO.setMapResponsesByIdEntry(null);
			appointmentDTO.setListResponse(listResponse);
		}
	}

	/**
	 * Validate the form and the additional entries of the form
	 * 
	 * @param appointmentDTO
	 *            the appointmentFron DTo to validate
	 * @param request
	 *            the request
	 * @param listFormErrors
	 *            the list of errors that can be fill with the errors found at
	 *            the validation
	 */
	public static void validateFormAndEntries(AppointmentDTO appointmentDTO, HttpServletRequest request,
			List<GenericAttributeError> listFormErrors) {
		Set<ConstraintViolation<AppointmentDTO>> listErrors = BeanValidationUtil.validate(appointmentDTO);
		if (CollectionUtils.isNotEmpty(listErrors)) {
			for (ConstraintViolation<AppointmentDTO> constraintViolation : listErrors) {
				GenericAttributeError genAttError = new GenericAttributeError();
				genAttError.setErrorMessage(
						I18nService.getLocalizedString(constraintViolation.getMessageTemplate(), request.getLocale()));
				listFormErrors.add(genAttError);
			}
		}
		List<Entry> listEntryFirstLevel = EntryHome
				.getEntryList(EntryService.buildEntryFilter(appointmentDTO.getIdForm()));
		for (Entry entry : listEntryFirstLevel) {
			listFormErrors.addAll(
					EntryService.getResponseEntry(request, entry.getIdEntry(), request.getLocale(), appointmentDTO));
		}
	}

	public static List<ResponseRecapDTO> buildListResponse(AppointmentDTO appointment, HttpServletRequest request,
			Locale locale) {
		List<ResponseRecapDTO> listResponseRecapDTO = new ArrayList<ResponseRecapDTO>();
		if (CollectionUtils.isNotEmpty(appointment.getListResponse())) {
			listResponseRecapDTO = new ArrayList<ResponseRecapDTO>(appointment.getListResponse().size());
			for (Response response : appointment.getListResponse()) {
				int nIndex = response.getEntry().getPosition();
				IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService(response.getEntry());
				AppointmentUtilities.addInPosition(
						nIndex, new ResponseRecapDTO(response, entryTypeService
								.getResponseValueForRecap(response.getEntry(), request, response, locale)),
						listResponseRecapDTO);
			}
		}
		return listResponseRecapDTO;
	}

	public static void buildExcelFileWithAppointments(String strIdResponse, HttpServletResponse response, Locale locale,
			List<AppointmentDTO> listAppointmentsDTO, StateService _stateService) {
		AppointmentForm tmpForm = FormService.buildAppointmentFormLight(Integer.parseInt(strIdResponse));
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet(I18nService.getLocalizedString(KEY_RESOURCE_TYPE, locale));
		List<Object[]> tmpObj = new ArrayList<Object[]>();
		EntryFilter entryFilter = new EntryFilter();
		entryFilter.setIdResource(Integer.valueOf(strIdResponse));
		List<Entry> listEntry = EntryHome.getEntryList(entryFilter);
		Map<Integer, String> mapDefaultValueGenAttBackOffice = new HashMap<Integer, String>();
		for (Entry e : listEntry) {
			if (e.isOnlyDisplayInBack()) {
				e = EntryHome.findByPrimaryKey(e.getIdEntry());
				if (e.getFields() != null && e.getFields().size() == 1
						&& !StringUtils.isEmpty(e.getFields().get(0).getValue())) {
					mapDefaultValueGenAttBackOffice.put(e.getIdEntry(), e.getFields().get(0).getValue());
				} else if (e.getFields() != null) {
					for (Field field : e.getFields()) {
						if (field.isDefaultValue()) {
							mapDefaultValueGenAttBackOffice.put(e.getIdEntry(), field.getValue());
						}
					}
				}
			}
		}
		int nTaille = 9 + (listEntry.size() + 1);
		if (tmpForm != null) {
			int nIndex = 0;
			Object[] strWriter = new String[1];
			strWriter[0] = tmpForm.getTitle();
			tmpObj.add(strWriter);
			Object[] strInfos = new String[nTaille];
			strInfos[0] = I18nService.getLocalizedString(KEY_COLUMN_LAST_NAME, locale);
			strInfos[1] = I18nService.getLocalizedString(KEY_COLUMN_FISRT_NAME, locale);
			strInfos[2] = I18nService.getLocalizedString(KEY_COLUMN_EMAIL, locale);
			strInfos[3] = I18nService.getLocalizedString(KEY_COLUMN_DATE_APPOINTMENT, locale);
			strInfos[4] = I18nService.getLocalizedString(KEY_TIME_START, locale);
			strInfos[5] = I18nService.getLocalizedString(KEY_TIME_END, locale);
			strInfos[6] = I18nService.getLocalizedString(KEY_COLUMN_STATUS, locale);
			strInfos[7] = I18nService.getLocalizedString(KEY_COLUM_LOGIN, locale);
			strInfos[8] = I18nService.getLocalizedString(KEY_COLUMN_STATE, locale);
			strInfos[9] = I18nService.getLocalizedString(KEY_COLUMN_NB_BOOKED_SEATS, locale);
			nIndex = 1;
			if (listEntry.size() > 0) {
				for (Entry e : listEntry) {
					strInfos[9 + nIndex] = e.getTitle();
					nIndex++;
				}
			}
			tmpObj.add(strInfos);
		}
		if (listAppointmentsDTO != null) {
			for (AppointmentDTO appointmentDTO : listAppointmentsDTO) {
				int nIndex = 0;
				Object[] strWriter = new String[nTaille];
				strWriter[0] = appointmentDTO.getLastName();
				strWriter[1] = appointmentDTO.getFirstName();
				strWriter[2] = appointmentDTO.getEmail();
				strWriter[3] = appointmentDTO.getDateOfTheAppointment();
				strWriter[4] = appointmentDTO.getStartingTime().toString();
				strWriter[5] = appointmentDTO.getEndingTime().toString();
				String status = I18nService.getLocalizedString(AppointmentDTO.PROPERTY_APPOINTMENT_STATUS_RESERVED,
						locale);
				if (appointmentDTO.getIsCancelled()) {
					status = I18nService.getLocalizedString(AppointmentDTO.PROPERTY_APPOINTMENT_STATUS_UNRESERVED,
							locale);
				}
				strWriter[6] = status;
				strWriter[7] = Integer.toString(appointmentDTO.getIdUser());
				State stateAppointment = _stateService.findByResource(appointmentDTO.getIdAppointment(),
						Appointment.APPOINTMENT_RESOURCE_TYPE, tmpForm.getIdWorkflow());
				String strState = StringUtils.EMPTY;
				if (stateAppointment != null) {
					appointmentDTO.setState(stateAppointment);
					strState = stateAppointment.getName();
				}
				strWriter[8] = strState;
				nIndex = 1;
				strWriter[9] = Integer.toString(appointmentDTO.getNbBookedSeats());
				List<Integer> listIdResponse = AppointmentResponseService
						.findListIdResponse(appointmentDTO.getIdAppointment());
				List<Response> listResponses = new ArrayList<Response>();
				for (int nIdResponse : listIdResponse) {
					Response resp = ResponseHome.findByPrimaryKey(nIdResponse);
					if (resp != null) {
						listResponses.add(resp);
					}
				}
				for (Entry e : listEntry) {
					Integer key = e.getIdEntry();
					String strValue = StringUtils.EMPTY;
					String strPrefix = StringUtils.EMPTY;
					for (Response resp : listResponses) {
						String strRes = StringUtils.EMPTY;
						if (key.equals(resp.getEntry().getIdEntry())) {
							Field f = resp.getField();
							int nfield = 0;
							if (f != null) {
								nfield = f.getIdField();
								Field field = FieldHome.findByPrimaryKey(nfield);
								if (field != null) {
									strRes = field.getTitle();
								}
							} else {
								strRes = resp.getResponseValue();
							}
						}
						if ((strRes != null) && !strRes.isEmpty()) {
							strValue += (strPrefix + strRes);
							strPrefix = CONSTANT_COMMA;
						}
					}
					if (strValue.isEmpty() && mapDefaultValueGenAttBackOffice.containsKey(key)) {
						strValue = mapDefaultValueGenAttBackOffice.get(key);
					}
					if (!strValue.isEmpty()) {
						strWriter[9 + nIndex] = strValue;
					}
					nIndex++;
				}
				tmpObj.add(strWriter);
			}
		}
		int nRownum = 0;
		for (Object[] myObj : tmpObj) {
			Row row = sheet.createRow(nRownum++);
			int nCellnum = 0;
			for (Object strLine : myObj) {
				Cell cell = row.createCell(nCellnum++);
				if (strLine instanceof String) {
					cell.setCellValue((String) strLine);
				} else if (strLine instanceof Boolean) {
					cell.setCellValue((Boolean) strLine);
				} else if (strLine instanceof Date) {
					cell.setCellValue((Date) strLine);
				} else if (strLine instanceof Double) {
					cell.setCellValue((Double) strLine);
				}
			}
		}
		try {
			String now = new SimpleDateFormat("yyyyMMdd-hhmm").format(GregorianCalendar.getInstance(locale).getTime())
					+ "_" + I18nService.getLocalizedString(KEY_RESOURCE_TYPE, locale) + EXCEL_FILE_EXTENSION;
			response.setContentType(EXCEL_MIME_TYPE);
			response.setHeader("Content-Disposition", "attachment; filename=\"" + now + "\";");
			response.setHeader("Pragma", "public");
			response.setHeader("Expires", "0");
			response.setHeader("Cache-Control", "must-revalidate,post-check=0,pre-check=0");
			OutputStream os = response.getOutputStream();
			workbook.write(os);
			os.close();
			workbook.close();
		} catch (IOException e) {
			AppLogService.error(e);
		}
	}

	/**
	 * add an object in a collection list
	 * 
	 * @param int
	 *            the index
	 * @param ResponseRecapDTO
	 *            the object
	 * @param List
	 *            <ResponseRecapDTO> the collection
	 */
	public static void addInPosition(int i, ResponseRecapDTO response, List<ResponseRecapDTO> list) {
		while (list.size() < i) {
			list.add(list.size(), null);
		}
		list.set(i - 1, response);
	}
}
