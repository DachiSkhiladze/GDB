package biologics.adapter.entityeventlistener;

import genedata.bx.adapter.entity.User;
import genedata.bx.adapter.entityeventlistener.EntityEventAction;
import genedata.bx.adapter.entityeventlistener.EntityEventListenerAdapter;
import genedata.bx.adapter.entityeventlistener.EntityEventListenerCallback;
import genedata.bx.adapter.entityeventlistener.EntityEventListenerInvocationContext;
import genedata.bx.adapter.entityeventlistener.EntityEventListenerOptions;
import genedata.bx.adapter.entityeventlistener.Event;
import genedata.bx.adapter.ws.InternalWebServiceInvoker;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.HttpMethod;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.Request;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.Response;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.WebServiceInvocationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * Basic logger which listens to the create/update/delete entity events 
 * occurring on the registered entity types.
 *  
 * <p>
 * The entity types could be registered by inserting a row to the ADAPTER_IMPLEMENTATION_ENTITY_CONFIGURATION table
 * containing the identifier of the entity_configuration of interest and the identifier of this adapter_implementation.
 * See the {@code EntityEventListenerAdapter/register/BasicEntityEventLogger.sql} script for reference.
 * </p>
 * 
 * <p>
 * The logger can additionally invoke the Export Entities Web Service for querying and logging the attributes of existing entities.
 * This can be enabled with inserting a row to the PARAMETER table with the 
 * 'biologics_adapter_entityeventlistener_BasicEntityEventLogger_report_entity_attributes' KEY and 'true' VALUE columns.
 * See the last section of {@code EntityEventListenerAdapter/register/BasicEntityEventLogger.sql} script for reference.
 * </p>
 *
 * <div style="font-size:x-small">
 * Copyright 2022 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BasicEntityEventLogger implements EntityEventListenerAdapter {
	
	private static final Logger log = Logger.getLogger(BasicEntityEventLogger.class);
	
	private static final String REPORT_ENTITY_ATTRIBUTES_KEY_SUFFIX = "report_entity_attributes";
	
	private boolean isReportingEntityAttributes = false;
	
	private Map<String, Set<Long>> deletedEntities = new HashMap<>();
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
	    String reportEntityAttributesParamKey = getClass().getName().replace('.', '_') + '_' + REPORT_ENTITY_ATTRIBUTES_KEY_SUFFIX;
		if (configuration != null && configuration.containsKey(reportEntityAttributesParamKey)) {
			String reportEntityAttributesConfigValue = configuration.get(reportEntityAttributesParamKey);
			isReportingEntityAttributes = Boolean.parseBoolean(reportEntityAttributesConfigValue);
		}
	}
	
	@Override
	public void options(EntityEventListenerOptions options) {
		// nothing to do here.
	}
	
	@Override
	public void perform(EntityEventListenerCallback callback) {
		log.debug("Entity Event Listener " + this.getClass().getName());
		
		User invokingUser = getUser(callback);
		List<Event> entityEvents = getEvents(callback);
		findDeletedEntities(entityEvents);
		for (Event event : entityEvents) {
			log.info(eventToString(event, invokingUser));
			if (canInvokeEntityExportWSForEvent(event)) {
				invokeEntityExportWS(callback, event);
			}
		}
	}
	
	private static User getUser(EntityEventListenerCallback callback) {
		EntityEventListenerInvocationContext context = callback.getContext();
		return context.getInvokingUser();
	}
	
	private static List<Event> getEvents(EntityEventListenerCallback callback) {
		EntityEventListenerInvocationContext context = callback.getContext();
		return context.getEvents();
	}
	
	/**
	 * Finds and stores all entity visibleIds which are affected by a Delete event.
	 * This collection could be used later via the
	 * {@link #hasEntityBeenDeleted(String, long)} method to avoid accessing entities
	 * via the Export Entities WS which already have been deleted from the system.
	 */
	private void findDeletedEntities(List<Event> events) {
		deletedEntities.clear();
		events.stream()
			.filter(e -> e.getAction() == EntityEventAction.Delete)
			.forEach(e -> deletedEntities.computeIfAbsent(e.getEntityType().getName(), k -> new HashSet<>()).addAll(e.getVisibleIds()));
	}
	
	private static String eventToString(Event event, User user) {
		StringBuilder eventBuilder = new StringBuilder();
		eventBuilder.append("Entity Event Occurred. User: ");
		eventBuilder.append(user.getAccount());
		eventBuilder.append("; Action: ");
		eventBuilder.append(event.getAction().toString());
		eventBuilder.append("; Entity Type: ");
		eventBuilder.append(event.getEntityType().getName());
		if (!event.getVisibleIds().isEmpty()) {
			eventBuilder.append("; Entity Visible ID(s): ");
			List<String> identifiers = event.getVisibleIds().stream().map(i -> i.toString()).collect(Collectors.toList());
			eventBuilder.append(String.join(", ", identifiers));
		} else {
			// in some cases the visibleIds are not available, so logging the # of affected entities
			eventBuilder.append("; Number of affected entities: ");
			eventBuilder.append(event.getNumberOfEntities());
		}
		
		return eventBuilder.toString();
	}
	
	/**
	 * The Export Entities WS can be invoked for the specified {@link Event} only when 
	 * <ul><li>The "report_entity_attributes" parameter has been set to true</li>
	 * <li>The event is not a Delete event</li>
	 * <li>The event contains at least one visibleId which is not affected by a Delete event</li></ul>
	 */
	private boolean canInvokeEntityExportWSForEvent(Event event) {	
		// the Export Entities Web Service requires existing entities and available visibleIds
		return isReportingEntityAttributes
				&& event.getAction() != EntityEventAction.Delete
				&& hasExistingEntity(event);
	}
	
	/**
	 * Checks whether the specified {@link Event} has at least one visibleId which is not affected by a Delete action.
	 * Useful to prevent accessing entities via the Export Entities WS which already have been deleted from the system.
	 */
	private boolean hasExistingEntity(Event event) {
		return event.getVisibleIds() != null && event.getVisibleIds().stream()
				.anyMatch(vid -> !hasEntityBeenDeleted(event.getEntityType().getName(), vid));
	}
	
	/**
	 * Checks whether the specified entity is affected by any Delete action.
	 * Useful to prevent accessing entities via the Export Entities WS which already have been deleted from the system.
	 */
	private boolean hasEntityBeenDeleted(String entityName, long visibleId) {
		Set<Long> deletedEntityIds = deletedEntities.get(entityName);
		return deletedEntityIds != null && deletedEntityIds.contains(visibleId); 
	}
	
	/**
	 * Invokes the Export Entities Web Service to obtain and log the attributes of the entities of the specified {@link Event}.
	 */
	private void invokeEntityExportWS(EntityEventListenerCallback callback, Event event) {
		String url = prepareRequestUrl(event);
		log.info("Request: GET " + url);
		
		InternalWebServiceInvoker wsInvoker = callback.getInternalWebServiceInvoker();
		Request request = wsInvoker.createRequest(HttpMethod.GET, url);
		
		try {
			Response response = wsInvoker.invoke(request);
			processResponse(response, url);
		} catch (WebServiceInvocationException e) {
			log.error(e.getMessage());
		}
	}
	
	private String prepareRequestUrl(Event event) {
		String internalEntityName = event.getEntityType().getName().toLowerCase();
		String entityIds = event.getVisibleIds().stream()
				.filter(vid -> !hasEntityBeenDeleted(event.getEntityType().getName(), vid))
				.map(vid -> vid.toString())
				.collect(Collectors.joining(","));
		
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append("/");
		pathBuilder.append(internalEntityName);
		pathBuilder.append("/");
		pathBuilder.append(entityIds);
		return pathBuilder.toString();
	}
	
	private static void processResponse(Response response, String url) throws WebServiceInvocationException {
		// check whether the web service reported an error
		if (response.getStatusCode() != 200) {
			log.error("Error occurred during request: " + url + ": " + response.getString());
		} else {
			log.info(response.getString());
		}
	}
}
