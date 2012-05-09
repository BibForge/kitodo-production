package de.sub.goobi.Persistence.apache;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.SystemUtils;

import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.InvalidImagesException;

public class FolderInformation {

	private int id;
	private String title;
	public static String metadataPath = ConfigMain.getParameter("MetadatenVerzeichnis");
	public static String DIRECTORY_SUFFIX = ConfigMain.getParameter("DIRECTORY_SUFFIX", "tif");
	public static String DIRECTORY_PREFIX = ConfigMain.getParameter("DIRECTORY_PREFIX", "orig");

	public FolderInformation(int id, String goobititle) {
		this.id = id;
		this.title = goobititle;
	}

	// String mainPath = metadataPath + this.id + File.separator;
	// mainPath = mainPath.replaceAll(" ", "__");
	// String processpath = this.mainPath.replace("\\", "/");
	// String imagepath = this.mainPath + "images" + File.separator;
	//
	// this.process.getImagesDirectory().replace("\\", "/");
	// String tifpath = this.process.getImagesTifDirectory().replace("\\", "/");
	// String origpath = this.process.getImagesOrigDirectory().replace("\\", "/");
	// String metaFile = this.process.getMetadataFilePath().replace("\\", "/");
	// String ocrBasisPath = this.process.getOcrDirectory().replace("\\", "/");
	// String ocrPlaintextPath = this.process.getTxtDirectory().replace("\\", "/");
	// String sourcepath = this.process.getSourceDirectory().replace("\\", "/");
	// String myprefs = ConfigMain.getParameter("RegelsaetzeVerzeichnis") + this.process.getRegelsatz().getDatei();

	public String getImagesTifDirectory() {
		File dir = new File(getImagesDirectory());
		DIRECTORY_SUFFIX = ConfigMain.getParameter("DIRECTORY_SUFFIX", "tif");
		DIRECTORY_PREFIX = ConfigMain.getParameter("DIRECTORY_PREFIX", "orig");
		/* nur die _tif-Ordner anzeigen, die nicht mir orig_ anfangen */
		FilenameFilter filterVerz = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return (name.endsWith("_" + DIRECTORY_SUFFIX) && !name.startsWith(DIRECTORY_PREFIX + "_"));
			}
		};

		String tifOrdner = "";
		String[] verzeichnisse = dir.list(filterVerz);

		if (verzeichnisse != null) {
			for (int i = 0; i < verzeichnisse.length; i++) {
				tifOrdner = verzeichnisse[i];
			}
		}

		if (tifOrdner.equals("")) {
			tifOrdner = this.title + "_" + DIRECTORY_SUFFIX;
		}

		String rueckgabe = getImagesDirectory() + tifOrdner;

		if (!rueckgabe.endsWith(File.separator)) {
			rueckgabe += File.separator;
		}
		// if (!ConfigMain.getBooleanParameter("useOrigFolder", true) && ConfigMain.getBooleanParameter("createOrigFolderIfNotExists", false)) {
		// if (!new File(rueckgabe).exists()) {
		// new Helper().createMetaDirectory(rueckgabe);
		// }
		// }
		return rueckgabe;
	}

	/*
	 * @return true if the Tif-Image-Directory exists, false if not
	 */
	public Boolean getTifDirectoryExists() {
		File testMe;

		testMe = new File(getImagesTifDirectory());

		if (testMe.list() == null) {
			return false;
		}
		if (testMe.exists() && testMe.list().length > 0) {
			return true;
		} else {
			return false;
		}
	}

	public String getImagesOrigDirectory() {
		if (ConfigMain.getBooleanParameter("useOrigFolder", true)) {
			File dir = new File(getImagesDirectory());
			DIRECTORY_SUFFIX = ConfigMain.getParameter("DIRECTORY_SUFFIX", "tif");
			DIRECTORY_PREFIX = ConfigMain.getParameter("DIRECTORY_PREFIX", "orig");
			/* nur die _tif-Ordner anzeigen, die mit orig_ anfangen */
			FilenameFilter filterVerz = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return (name.endsWith("_" + DIRECTORY_SUFFIX) && name.startsWith(DIRECTORY_PREFIX + "_"));
				}
			};

			String origOrdner = "";
			String[] verzeichnisse = dir.list(filterVerz);
			for (int i = 0; i < verzeichnisse.length; i++) {
				origOrdner = verzeichnisse[i];
			}
			if (origOrdner.equals("")) {
				origOrdner = DIRECTORY_PREFIX + "_" + this.title + "_" + DIRECTORY_SUFFIX;
			}
			String rueckgabe = getImagesDirectory() + origOrdner + File.separator;
			// if (!new File(rueckgabe).exists() && ConfigMain.getBooleanParameter("createOrigFolderIfNotExists", false)) {
			// new Helper().createMetaDirectory(rueckgabe);
			// }
			return rueckgabe;
		} else {
			return getImagesTifDirectory();
		}
	}

	public String getImagesDirectory() {
		String pfad = getProcessDataDirectory() + "images" + File.separator;

		return pfad;
	}

	public String getProcessDataDirectory() {
		String pfad = metadataPath + this.id + File.separator;
		pfad = pfad.replaceAll(" ", "__");
		return pfad;
	}

	public String getOcrDirectory() {
		return getProcessDataDirectory() + "ocr" + File.separator;
	}

	public String getTxtDirectory() {
		return getOcrDirectory() + this.title + "_txt" + File.separator;
	}

	public String getWordDirectory() {
		return getOcrDirectory() + this.title + "_wc" + File.separator;
	}

	public String getPdfDirectory() {
		return getOcrDirectory() + this.title + "_pdf" + File.separator;
	}

	public String getAltoDirectory() {
		// TODO FIXME
		return getOcrDirectory() + this.title + "_xml" + File.separator;
	}

	public String getSourceDirectory() {
		return getProcessDataDirectory() + "source" + File.separator;
	}

	public String getMetadataFilePath() {
		return getProcessDataDirectory() + "meta.xml";
	}

	public Map<String, String> getFolderForProcess() {
		Map<String, String> answer = new HashMap<String, String>();
		String processpath = getProcessDataDirectory().replace("\\", "/");
		String tifpath = getImagesTifDirectory().replace("\\", "/");
		String imagepath = getImagesDirectory().replace("\\", "/");
		String origpath = getImagesOrigDirectory().replace("\\", "/");
		String metaFile = getMetadataFilePath().replace("\\", "/");
		String ocrBasisPath = getOcrDirectory().replace("\\", "/");
		String ocrPlaintextPath = getTxtDirectory().replace("\\", "/");
		String sourcepath = getSourceDirectory().replace("\\", "/");
		if (tifpath.endsWith(File.separator)) {
			tifpath = tifpath.substring(0, tifpath.length() - File.separator.length()).replace("\\", "/");
		}
		if (imagepath.endsWith(File.separator)) {
			imagepath = imagepath.substring(0, imagepath.length() - File.separator.length()).replace("\\", "/");
		}
		if (origpath.endsWith(File.separator)) {
			origpath = origpath.substring(0, origpath.length() - File.separator.length()).replace("\\", "/");
		}
		if (processpath.endsWith(File.separator)) {
			processpath = processpath.substring(0, processpath.length() - File.separator.length()).replace("\\", "/");
		}
		if (sourcepath.endsWith(File.separator)) {
			sourcepath = sourcepath.substring(0, sourcepath.length() - File.separator.length()).replace("\\", "/");
		}
		if (ocrBasisPath.endsWith(File.separator)) {
			ocrBasisPath = ocrBasisPath.substring(0, ocrBasisPath.length() - File.separator.length()).replace("\\", "/");
		}
		if (ocrPlaintextPath.endsWith(File.separator)) {
			ocrPlaintextPath = ocrPlaintextPath.substring(0, ocrPlaintextPath.length() - File.separator.length()).replace("\\", "/");
		}
		if (SystemUtils.IS_OS_WINDOWS) {
			answer.put("(tifurl)", "file:/" + tifpath);
		} else {
			answer.put("(tifurl)", "file://" + tifpath);
		}
		if (SystemUtils.IS_OS_WINDOWS) {
			answer.put("(origurl)", "file:/" + origpath);
		} else {
			answer.put("(origurl)", "file://" + origpath);
		}
		if (SystemUtils.IS_OS_WINDOWS) {
			answer.put("(imageurl)", "file:/" + imagepath);
		} else {
			answer.put("(imageurl)", "file://" + imagepath);
		}
		answer.put("(tifpath)", tifpath);
		answer.put("(origpath)", origpath);
		answer.put("(imagepath)", imagepath);
		answer.put("(processpath)", processpath);
		answer.put("(sourcepath)", sourcepath);
		answer.put("(ocrbasispath)", ocrBasisPath);
		answer.put("(ocrplaintextpath)", ocrPlaintextPath);
		answer.put("(metaFile)", metaFile);
		return answer;
	}

	public String getMethodFromName(String methodName) {
		java.lang.reflect.Method method;
		try {
			method = this.getClass().getMethod(methodName);
			Object o = method.invoke(this);
			return (String) o;
		} catch (SecurityException e) {

		} catch (NoSuchMethodException e) {

		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
		String folder = this.getImagesTifDirectory();
		folder = folder.substring(0, folder.lastIndexOf("_"));
		folder = folder + "_" + methodName;
		if (new File(folder).exists()) {
			return folder;
		}
		return null;
	}
	
	public List<String> getDataFiles() throws InvalidImagesException {
		File dir;
		try {
			dir = new File(getImagesTifDirectory());
			// throw new NullPointerException("wer das liest ist doof");
		} catch (Exception e) {
			throw new InvalidImagesException(e);
		}
		/* Verzeichnis einlesen */
		String[] dateien = dir.list(Helper.dataFilter);
		ArrayList<String> dataList = new ArrayList<String>();
		if (dateien != null && dateien.length > 0) {
			for (int i = 0; i < dateien.length; i++) {
				String s = dateien[i];
				dataList.add(s);
			}
			/* alle Dateien durchlaufen */
			if (dataList != null && dataList.size() != 0) {
				Collections.sort(dataList,  new GoobiImageFileComparator());
			}
			return dataList;
		} else {
			return null;
		}
	}
	
	public static class GoobiImageFileComparator implements Comparator<String> {

		@Override
		public int compare(String s1, String s2) {
			String imageSorting = ConfigMain.getParameter("ImageSorting", "number");
			s1 = s1.substring(0, s1.lastIndexOf("."));
			s2 = s2.substring(0, s2.lastIndexOf("."));

			if (imageSorting.equalsIgnoreCase("number")) {
				try {
					Integer i1 = Integer.valueOf(s1);
					Integer i2 = Integer.valueOf(s2);
					return i1.compareTo(i2);
				} catch (NumberFormatException e) {
					return s1.compareToIgnoreCase(s2);
				}
			} else if (imageSorting.equalsIgnoreCase("alphanumeric")) {
				return s1.compareToIgnoreCase(s2);
			} else {
				return s1.compareToIgnoreCase(s2);
			}
		}

	}
}
