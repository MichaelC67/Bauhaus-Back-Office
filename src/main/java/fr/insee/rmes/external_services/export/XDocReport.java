package fr.insee.rmes.external_services.export;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import fr.insee.rmes.bauhaus_services.operations.operations.VarBookExportBuilder;
import fr.insee.rmes.exceptions.RmesException;
import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.core.XDocReportException;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import freemarker.ext.dom.NodeModel;

@Component
public class XDocReport {

	@Autowired
	VarBookExportBuilder varBookExport;

	static final Logger logger = LogManager.getLogger(XDocReport.class);

	public OutputStream exportVariableBookInPdf(String xmlFilename, String odtTemplate) throws RmesException  {
		// 1) Load DOCX into XWPFDocument
		IXDocReport report;
		OutputStream oFile = null;
		try {
			report = getReportTemplate(odtTemplate);
		// 2) Create Java model context 
		IContext context = getXmlData(report, xmlFilename);

		// 3) Generate report by merging Java model with the ODT and convert it to PDF
			oFile = createOutputFile(true); 
			Options options = Options.getTo(ConverterTypeTo.PDF);

			report.convert(context, options, oFile);
		} catch (XDocReportException | IOException e) {
			logger.error(e.getMessage());
		}
		return oFile;		

	}

	@Deprecated
	public OutputStream exportVariableBookInOdt(String xml, String odtTemplate) throws IOException, XDocReportException, RmesException {
		// 1) Load DOCX into XWPFDocument
		IXDocReport report = getReportTemplate(odtTemplate);

		// 2) Create Java model context 
		IContext context = getXmlData(report, xml);

		// 3) Generate report by merging Java model with the ODT
		OutputStream oFile = createOutputFile(false); 
		report.process(context, oFile);
		return oFile;
	}

	public OutputStream exportVariableBookInOdt(String xml, File odtTemplate) throws RmesException {
		IXDocReport report;
		OutputStream oFile = null;
		
		// 1) Load DOCX into XWPFDocument
		try {
			report = getReportTemplate(odtTemplate);
			
			// 2) Create Java model context 
			IContext context = getXmlData(report, xml);
	
			// 3) Generate report by merging Java model with the ODT
			oFile = createOutputFile(false); 
			report.process(context, oFile);
		}catch (IOException | XDocReportException e) {
			logger.error(e.getMessage());
		}
		return oFile;
	}



	private IContext getXmlData(IXDocReport report, String xmlInput)
			throws  RmesException {

		String xmlString = varBookExport.getData(xmlInput);
		InputStream projectInputStream = new ByteArrayInputStream(xmlString.getBytes());
		InputSource projectInputSource = new InputSource( projectInputStream );
		NodeModel xml = null;
		IContext context = null ;
		try {
			xml = NodeModel.parse( projectInputSource );
			context = report.createContext();
		} catch (SAXException | IOException | ParserConfigurationException | XDocReportException e) {
			throw new RmesException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e.getClass()+" - Can't put xml in XdocReport context");
		}
		context.put("racine", xml);
		return context;
	}


	private OutputStream createOutputFile(boolean isPdf) throws IOException {
		//TODO pass acceptHeader to manage more than pdf/odt
		File outFile = File.createTempFile("Codebook", (isPdf?".pdf":".odt"));
		return new FileOutputStream(outFile);
	}


	@Deprecated
	//TODO use this for default value when template is ok
	private IXDocReport getReportTemplate(String odtTemplate) throws IOException, XDocReportException {
		try(InputStream is = getClass().getClassLoader().getResourceAsStream("xdocreport/"+odtTemplate)){
				return  XDocReportRegistry.getRegistry().loadReport(is,TemplateEngineKind.Freemarker);
		}
	}

	private IXDocReport getReportTemplate(File odtTemplate) throws IOException, XDocReportException {
		try(InputStream is = new FileInputStream(odtTemplate)){
			return  XDocReportRegistry.getRegistry().loadReport(is,TemplateEngineKind.Freemarker);
		}

	}

}
