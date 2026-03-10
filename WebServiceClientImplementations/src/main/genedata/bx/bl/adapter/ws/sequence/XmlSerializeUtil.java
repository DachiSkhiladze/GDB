package genedata.bx.bl.adapter.ws.sequence;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

/**
 * Utility class for common functions required by web service client implementations.
 */
public class XmlSerializeUtil {

	/**
	 * Unmarshals an Object<T> from an input stream to a given JAXB-compliant
	 * class T. Schema is optional for extra validation.
	 * @param validationEventHandler 
	 */
	public static <T> T unmarshal(InputStream inputStream, Class<T> rootClass, Schema schema, ValidationEventHandler validationEventHandler) throws JAXBException, SAXException {
		final String thePackageWithXmlMapping = rootClass.getPackage().getName();
		
		// Explicitly specify class-loader (required in J2EE environments)
		JAXBContext context = JAXBContext.newInstance(thePackageWithXmlMapping , rootClass.getClassLoader());
		Unmarshaller unmarshaller = context.createUnmarshaller();

		if (schema != null) {
			// Validate against schema
			unmarshaller.setSchema(schema);
		}
		
		if (validationEventHandler != null) {
			unmarshaller.setEventHandler(validationEventHandler);
		}
		
		Source source = new StreamSource(inputStream);
		JAXBElement<T> rmcObject =  unmarshaller.unmarshal(source, rootClass);		
		return rmcObject.getValue();
	}

	/**
	 * Marshals a JAXBElement<T> into a stream. with optional validation
	 * against XML schema.
	 */
	public static <T> void marshal(OutputStream outputStream, JAXBElement<T> jaxbElement, Schema schema) throws JAXBException {
		Class<T> declaredType = jaxbElement.getDeclaredType();
		final String thePackageWithXmlMapping = declaredType.getPackage().getName();
		
		// Explicitly specify class-loader (required in J2EE environments)
		JAXBContext context = JAXBContext.newInstance(thePackageWithXmlMapping, declaredType.getClassLoader());
		Marshaller marshaller = context.createMarshaller();

		if (schema != null) {
			// Validate against schema
			marshaller.setSchema(schema);
		}
		
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
		marshaller.marshal(jaxbElement, outputStream);
	}
	
	
	/**
	 * Create new XML Schema object from input stream.
	 * XML namespace awareness is on.
	 */
	public static Schema newSchema(InputStream schemaInputStream) throws SAXException {
		SchemaFactory sf = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
		return sf.newSchema(new StreamSource(schemaInputStream));
	}

	
	/**
	 * Log but do not abort on ValidationEvents
	 * @param log log4j logger
	 * @return ValidationEventHandler for unmarshaller
	 */
	public static ValidationEventHandler logValidationEventHandler(final Logger log) {
		return new ValidationEventHandler() {

			@Override
			public boolean handleEvent(ValidationEvent event) {
				String msg = event.getMessage();
				// Throwable ex = event.getLinkedException();
				
				log.warn("XML validation: " + msg);
				
				return true; /* do not abort */
			}
			
		};
	}
}
