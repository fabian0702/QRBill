package net.qrbill.printer;

import java.awt.print.Pageable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import javax.swing.JOptionPane;
import net.codecrete.qrbill.generator.Address;
import net.codecrete.qrbill.generator.Bill;
import net.codecrete.qrbill.generator.BillFormat;
import net.codecrete.qrbill.generator.GraphicsFormat;
import net.codecrete.qrbill.generator.Language;
import net.codecrete.qrbill.generator.OutputSize;
import net.codecrete.qrbill.generator.QRBill;
import net.codecrete.qrbill.generator.SeparatorType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import net.codecrete.qrbill.canvas.PDFCanvas;

public class QRBillGenerator {
  public static class MyFormatter extends Formatter {
    public String format(LogRecord record) {
      String log = "[" + new Date(record.getMillis()) + "] [" + record.getLevel() + "] " + record.getSourceClassName() + "." + record.getSourceMethodName() + " " + record.getMessage() + "\n";
      return log;
    }
  }
  
  private static int[] lineNumbersToTest = new int[] { 7, 8, 9, 10, 15, 16, 18, 19 };
  
  private static String getStackTrace(Exception e) {
    StackTraceElement[] logStack = e.getStackTrace();
    String strLogStack = String.valueOf(e.toString()) + "\n";
    for (StackTraceElement ste : logStack)
      strLogStack = strLogStack + "\tat " + ste.toString() + "\n";
    return strLogStack;
  }
  
  private static String readFile(String filePath) {
    String content = "";
    try {
      File myObj = new File(filePath);
      try (Scanner myReader = new Scanner(myObj, "utf-8")) {
        for (; myReader.hasNextLine(); content = content + myReader.nextLine() + "\n");
        myReader.close();
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      System.out.println(e.getStackTrace().toString());
      logger.log(Level.SEVERE, "configuration file not found", e);
      infoBox("Konfigurations Datei nicht gefunden", "Fehler");
    } 
    return content;
  }
  
  private static String[] parseJson(String JSON) {
    String filePath = "", IBAN = "", adress = "", logPath = "";
    Boolean dialog = Boolean.valueOf(false);
    JSON = JSON.replace("{", "");
    JSON = JSON.replace("}", "");
    String[] lines = JSON.split("\n");

    for (String line : lines) {
      if (!line.equals("")) {
        String[] parts = line.split(":");
        int startIndex = parts[0].indexOf("\""), endIndex = parts[0].lastIndexOf("\"");
        String key = parts[0].substring(startIndex + 1, endIndex);
        String value = parts[1].trim().replace("\"", "");
        if (value.endsWith(","))
          value = value.substring(0, value.length() - 1); 
        System.out.println(String.valueOf(key) + " " + value);
        switch (key) {
          case "adress":
            adress = value.replace("..", ":");
            break;
          case "dialog":
            dialog = value.contains("true");
            break;
          case "filePath":
            filePath = value.replace("..", ":");
            break;
          case "IBAN":
            IBAN = value;
            break;
          case "logPath":
            logPath = value.replace("..", ":");
            break;
        } 
      } 
    } 
    return new String[] { dialog.toString(), IBAN, filePath, adress, logPath };
  }
  
  static Logger logger = Logger.getLogger(QRBill.class.getName());
  
  public static class MyHandler extends StreamHandler {
    public void publish(LogRecord record) {
      super.publish(record);
    }
    
    public void flush() {
      super.flush();
    }
    
    public void close() throws SecurityException {
      super.close();
    }
  }
  
  public static void main(String[] args) {
    System.out.println(System.getProperty("user.home"));
    String configPath = String.valueOf(System.getProperty("user.dir")) + "/config.json";
    String JSON = readFile(configPath);
    String[] argsB = parseJson(JSON);
    logger.setLevel(Level.FINE);
    logger.addHandler(new ConsoleHandler());
    logger.addHandler(new MyHandler());
    try {
      Handler fileHandler = new FileHandler("C:/users/loris/downloads/log%u.log", 50000, 50000);
      fileHandler.setFormatter(new MyFormatter());
      logger.addHandler(fileHandler);
      logger.log(Level.CONFIG, "logger started");
    } catch (SecurityException|IOException e) {
      e.printStackTrace();
      System.out.println(e.getStackTrace().toString());
      return;
    } 
    generateBill(argsB);
  }
  
  private static void generateBill(String[] args) {
    Boolean dialog = Boolean.valueOf(args[0]);
    String IBAN = args[1];
    String filePath = args[2];
    String[] adress = args[3].split("-");
    if (Files.notExists(Paths.get(filePath, new String[] { "done" }), new java.nio.file.LinkOption[0]))
      Paths.get(filePath, new String[] { "done" }).toFile().mkdir(); 
    File folder = new File(filePath);
    File[] listOfFiles = folder.listFiles();
    System.out.println(listOfFiles);
    if (listOfFiles == null || listOfFiles.length <= 0) {
      logger.log(Level.SEVERE, "no files in " + filePath + " found");
      return;
    } 
    for (File file : listOfFiles) {
      System.out.println(file.getName());
      if (file.getName().contains(".prn"))
        try {
          logger.log(Level.INFO, "PRN File found: " + file.getName());
          Scanner myReader = new Scanner(file, "Cp1252");
          while (myReader.hasNextLine()) {
            String Line = myReader.nextLine();
            System.out.println(Line);
            String[] parts = Line.split("\t");

            for (int line : lineNumbersToTest) 
              if (parts[line] == "")
                throw new EmptyStackException(); 
             
            if (parts.length < 18)
              throw new ArrayIndexOutOfBoundsException("Array to short"); 
            Bill bill = new Bill();
            bill.setAccount(IBAN);
            bill.setAmountFromDouble(Double.valueOf(Double.parseDouble(parts[16])));
            bill.setCurrency(parts[15]);
            Address creditor = new Address();
            creditor.setName(adress[0]);
            creditor.setAddressLine1(adress[1]);
            creditor.setAddressLine2(adress[2]);
            creditor.setCountryCode("CH");
            bill.setCreditor(creditor);
            bill.setReference(parts[18]);
            Address debtor = new Address();
            debtor.setName(parts[7]);
            debtor.setAddressLine1(parts[8]);
            debtor.setAddressLine2(String.valueOf(parts[9]) + " " + parts[10]);
            debtor.setCountryCode("CH");
            bill.setDebtor(debtor);
            BillFormat format = new BillFormat();
            format.setGraphicsFormat(GraphicsFormat.SVG);
            format.setOutputSize(OutputSize.QR_BILL_EXTRA_SPACE);
            format.setLanguage(Language.DE);
            format.setSeparatorType(SeparatorType.DASHED_LINE_WITH_SCISSORS);
            bill.setFormat(format);
            String fileName = parts[19];
            String rechId = fileName.substring(0, 5);
            System.out.println(rechId);

            for (File filePdf : listOfFiles)
              if (filePdf.getName().contains(rechId) && filePdf.getName().contains("pdf"))
                fileName = filePdf.getName(); 
  
            Path invoiceWithoutQRBill = Paths.get(String.valueOf(filePath) + fileName, new String[0]), PathOut = Paths.get(filePath, new String[] { "done" });
            System.out.println(PathOut);
            try (PDFCanvas canvas = new PDFCanvas(invoiceWithoutQRBill, PDFCanvas.LAST_PAGE)) {
              QRBill.draw(bill, canvas);
              Path path = Paths.get("/done/" + fileName);
              canvas.saveAs(path);
              sendToPrinter(path, dialog);
            } catch (FileNotFoundException e) {
              System.out.println("PDF File not found...");
              e.printStackTrace();
              System.out.println(e.getStackTrace().toString());
              logger.log(Level.SEVERE, "PDF file not found", e);
              infoBox("PDF Datei nicht gefunden", "Fehler");
              return;
            } catch (NoSuchFileException e) {
              System.out.println("PDF File not found...");
              e.printStackTrace();
              System.out.println(e.getStackTrace().toString());
              logger.log(Level.SEVERE, "PDF file not found", e);
              infoBox("PDF Datei nicht gefunden oder keine Zugriffsrecht fdie Datei", "Fehler");
              return;
            } catch (IOException e) {
              System.out.println("Faulty Data...");
              e.printStackTrace();
              System.out.println(e.getStackTrace().toString());
              logger.log(Level.SEVERE, "PDF file io exeption", e);
              infoBox("Ein Problem mit der PDF Datei ist aufgetreten", "Fehler");
              return;
            } 
          } 
          myReader.close();
          file.delete();
        } catch (FileNotFoundException e) {
          System.out.println("Bills File not found...");
          System.out.println(e.getStackTrace().toString());
          logger.log(Level.SEVERE, "PRN file not found", e);
          infoBox("PRN Datei konnte nicht gefunden werden", "Fehler");
          return;
        } catch (Exception e) {
          System.out.println("Error while parsing File...");
          System.out.println(e.getStackTrace().toString());
          logger.log(Level.SEVERE, "Error while parsing File", e);
          infoBox("Ein Problem mit der PRN Datei ist aufgetreten", "Fehler");
          return;
        }
    } 
  }

    public static void sendToPrinter(Path filePath, Boolean dialog) {
    try {
      logger.log(Level.INFO, "Printing PDF in progess");
      PDDocument document = PDDocument.load(filePath.toFile());
      PrinterJob job = PrinterJob.getPrinterJob();
      job.setPageable((Pageable)new PDFPageable(document));
      if (!dialog.booleanValue()) {
        logger.log(Level.INFO, "Printing without dialog selected");
        job.print();
        logger.log(Level.INFO, "Printing PDF done");
      } else {
        logger.log(Level.INFO, "Printing with dialog selected");
        if (job.printDialog())
          try {
            job.print();
            logger.log(Level.INFO, "Printing PDF done");
          } catch (PrinterException exc) {
            System.out.println(exc);
            System.out.println(exc.getStackTrace().toString());
            logger.log(Level.SEVERE, "Error with selected printer ", exc);
            infoBox("Drucken fehlgeschlagen", "Fehler");
            return;
          }  
      } 
    } catch (Exception e) {
      infoBox("Ducken fehlgeschlagen", "Fehler");
      logger.log(Level.SEVERE, "Error while printing ", e);
      System.out.println("Print failed...");
      System.out.println(e.getStackTrace().toString());
      return;
    } 
  }

  public static void infoBox(String infoMessage, String titleBar) {
    JOptionPane.showMessageDialog(null, infoMessage, titleBar, 0);
  }
}
