package net.internetmemory.genreclassification;

import net.internetmemory.util.scraping.HtmlUtils;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONArray;
import org.json.JSONObject;
import weka.attributeSelection.ChiSquaredAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ClassificationWekaModel implements Serializable{
	private RandomForest pornClassifier;
	private RandomForest otherClassifier;
	private List<String> pornStatisticsFeatures = new ArrayList<>();
	private List<String> otherStatisticsFeatures = new ArrayList<>();
	private JSONArray jsonArray;
	static Set<String> currencies;
	static Set<String> pornKeyWordSet = new HashSet<>();

	private boolean initialized = false;

	static String modelFolder = "/tmp/";
	static String trainingJsonPath = "/home/jie/projects/GenreClassification/annotation_new.json";
	static String currenciesFilePath = "/home/jie/projects/GenreClassification/model/currency.set";
	static String badwordsPath = "/home/jie/projects/GenreClassification/model/badwords.txt";

	static final List<String> otherClasses = Arrays.asList("Forum", "News", "Blogs","Marketplace", "Spam");
	static final List<String> pornClasses = Arrays.asList("Porn", "Non-Porn");

	private void initialize() throws IOException, ClassNotFoundException {
	    //File annotation = new File("/home/jie/projects/GenreClassification/annotation_new.json");
		File annotation = new File(trainingJsonPath);
		currencies = (Set<String>) new ObjectInputStream(
				new FileInputStream(currenciesFilePath)).readObject();
		String content = FileUtils.readFileToString(annotation);
		jsonArray = new JSONArray(content);
		initialized = true;
		List<String> pornKeyWords = FileUtils.readLines(new File(badwordsPath));
		pornKeyWordSet.addAll(pornKeyWords.stream().map(String::toLowerCase).collect(Collectors.toList()));
	}

	private Instances buildFilteredInstances(Instances trainData, int nbAttribute) throws Exception{
		AttributeSelection attributeSelection = new AttributeSelection();
		Ranker ranker = new Ranker();
		ranker.setNumToSelect(nbAttribute);
		ChiSquaredAttributeEval chi2 = new ChiSquaredAttributeEval();
		attributeSelection.setEvaluator(chi2);
		attributeSelection.setSearch(ranker);
		attributeSelection.setInputFormat(trainData);
		return Filter.useFilter(trainData, attributeSelection);
	}
	
	private void createTrainData() throws Exception {
		if (!initialized)
			initialize();
		
		List<String> listPage = new ArrayList<>();
		List<String> listLabel = new ArrayList<>();
		List<String> listUrl = new ArrayList<>();

		for(Object obj: jsonArray){
		    JSONObject jsonObj = (JSONObject) obj;
			String url = jsonObj.getString("url");
			String label = jsonObj.getString("label");
			String content = jsonObj.getString("html");

			listPage.add(content);
			listLabel.add(label);
			listUrl.add(url);
		}

        //porn classifier
		List<Triple<String, String, String>> pornDataset = new ArrayList<>();
        for(int i=0; i<listLabel.size(); i++){
        	if(!listLabel.get(i).equals("Porn")){
				pornDataset.add(Triple.of(listPage.get(i), listUrl.get(i), "Non-Porn"));
			}else{
				pornDataset.add(Triple.of(listPage.get(i), listUrl.get(i), listLabel.get(i)));
			}
        }
        Collections.shuffle(pornDataset);
		ClassificationBuildData pornTrain = new ClassificationBuildData(pornClasses);
		pornTrain.buildTrainData(pornDataset);
		pornClassifier = new RandomForest();
		pornClassifier.setNumIterations(500);
		Instances filteredPornTrain = buildFilteredInstances(pornTrain.getStatisticsData(), 100);
		pornStatisticsFeatures = Collections.list(filteredPornTrain.enumerateAttributes())
				.stream().map(Attribute::name)
				.collect(Collectors.toList());
		pornClassifier.buildClassifier(filteredPornTrain);

		//other classifier
		List<Triple<String, String, String>> otherDataset = new ArrayList<>();
		for(int i=0; i<listLabel.size(); i++){
			if(!listLabel.get(i).equals("Porn")){
				otherDataset.add(Triple.of(listPage.get(i), listUrl.get(i), listLabel.get(i)));
			}
		}
		Collections.shuffle(otherDataset);
		ClassificationBuildData otherTrain = new ClassificationBuildData(otherClasses);
		otherTrain.buildTrainData(otherDataset);
		otherClassifier = new RandomForest();
		otherClassifier.setNumIterations(500);
		Instances filteredOtherTrain = buildFilteredInstances(otherTrain.getStatisticsData(), 200);
		otherStatisticsFeatures = Collections.list(filteredOtherTrain.enumerateAttributes())
				.stream().map(Attribute::name)
				.collect(Collectors.toList());
		otherClassifier.buildClassifier(filteredOtherTrain);

		//save model
		Object[] toSerialize = new Object[]{pornStatisticsFeatures, otherStatisticsFeatures,
				pornClassifier, otherClassifier, currencies, pornKeyWordSet};
		ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(modelFolder + "model.bin"));
		oout.writeObject(toSerialize);
		oout.flush();
		oout.close();
	}


	void loadModel(String modelPath) throws IOException, ClassNotFoundException {
		ObjectInput oin = new ObjectInputStream(new FileInputStream(modelPath));
		Object[] objects = (Object[]) oin.readObject();
		pornStatisticsFeatures = (List<String>) objects[0];
		otherStatisticsFeatures = (List<String>) objects[1];
		pornClassifier = (RandomForest) objects[2];
		otherClassifier = (RandomForest) objects[3];
		currencies = (Set<String>) objects[4];
		pornKeyWordSet = (Set<String>) objects[5];
	}

    /**
     * Take byte representation of string and return string, try to detect encoding
     * @param bytes
     * @return
     */
    public static String getStringFromBytesWithEncoding(byte[] bytes) {
        String encoding = HtmlUtils.detectEncodingAlt(bytes);
        try {
            return  new String(bytes, encoding);
        } catch (UnsupportedEncodingException e) {
            return new String(bytes);
        }
    }

    String getStringPrediction(double[] proba){
    	if(proba[5] > 0.5){
    		return "Porn";
		}else{
    		double maxi = 0;
    		int maxIndex = 0;
    		for(int i=0; i<5; i++){
    			if(proba[i] > maxi){
    				maxIndex = i;
    				maxi = proba[i];
				}
			}
			return otherClasses.get(maxIndex);
		}
	}

	
	String predict(String url) throws Exception {
		String html;
		InputStream in = new URL(url).openStream();
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			IOUtils.copy(in, baos);
			byte[] bytes = baos.toByteArray();
			html = getStringFromBytesWithEncoding(bytes);

		} finally {
			IOUtils.closeQuietly(in);
		}
		return getStringPrediction(predict(html, url));
	}

	String predict(byte[] htmlBytes, String url) throws Exception {
		return getStringPrediction(predict(getStringFromBytesWithEncoding(htmlBytes), url));
	}

	double[] predict(String htmlString, String url) throws Exception {
		ClassificationBuildData pornBuild = new ClassificationBuildData(pornClasses, pornStatisticsFeatures);
		ClassificationBuildData otherBuild = new ClassificationBuildData(otherClasses, otherStatisticsFeatures);
		List<Triple<String, String, String>> triple = Collections.singletonList(Triple.of(htmlString, url, null));
		pornBuild.buildTestData(triple);
		otherBuild.buildTestData(triple);
		Instances pornStatisticsData = pornBuild.getStatisticsData();
		Instances otherStatisticsData = otherBuild.getStatisticsData();
		/*
		List<String> features = new ArrayList<>();
		for(int i = 0; i<pornStatisticsData.numAttributes(); i++){
			features.add(pornStatisticsData.attribute(i).name() + "\t" + pornStatisticsData.firstInstance().value(i));
		}
		features.sort(String::compareToIgnoreCase);
		features.forEach(System.out::println);
		System.out.println("\n\n");*/
		String pornResult = pornClasses.get((int) pornClassifier.classifyInstance(pornStatisticsData.firstInstance()));
		String otherResult = otherClasses.get((int) otherClassifier.classifyInstance(otherStatisticsData.firstInstance()));
		double[] pornDist = Arrays.copyOfRange(pornClassifier.distributionForInstance(pornStatisticsData.firstInstance()), 0, 2);
		double[] otherDist = otherClassifier.distributionForInstance(otherStatisticsData.firstInstance());
		double[] proba = ArrayUtils.addAll(otherDist, pornDist);
		for(int i=0; i<otherClasses.size(); i++){
			System.out.println(otherClasses.get(i)+":\t"+proba[i]);
		}
		String result = proba[otherClasses.size()] > 0.5 ? "Porn" : otherResult;
		System.out.println("URL: " + url);
		System.out.printf("Predicted as:\t%s\n", result);
		return proba;
	}


	private void processInput(String[] args) throws ParseException, IOException {
		Options options = new Options();
		options.addOption("trainingPath", true, "path to JSON conrtaining training data");
		options.addOption("modelPath", true, "folder path where models will be put");
		options.addOption("currenciesPath", true, "path to file with currencies");
		options.addOption("badwordsPath", true, "path to the file of lists of porn keywords");
		options.addOption("h", false, "display this help");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		if (cmd.hasOption("h")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("Predict", options);
			System.exit(1);
		}

		if (!cmd.hasOption("modelPath") || cmd.getOptionValue("modelPath").isEmpty()) {
			System.out.println("Please specify model folder path.");
			System.exit(1);
		}

		if(!cmd.hasOption("trainingPath") || cmd.getOptionValue("trainingPath").isEmpty()){
			System.out.println("Please specify path to JSON with training data.");
			System.exit(1);
		}
		if(!cmd.hasOption("currenciesPath") || cmd.getOptionValue("currenciesPath").isEmpty()){
			System.out.println("Please specify path to currencies file.");
			System.exit(1);
		}
		if(!cmd.hasOption("badwordsPath") || cmd.getOptionValue("badwordsPath").isEmpty()){
			System.out.println("Please specify path to badwords file.");
			System.exit(1);
		}

		trainingJsonPath = cmd.getOptionValue("trainingPath");
		modelFolder = cmd.getOptionValue("modelPath");
		if (!modelFolder.endsWith("/"))
			modelFolder+="/";
		currenciesFilePath = cmd.getOptionValue("currenciesPath");
		badwordsPath = cmd.getOptionValue("badwordsPath");
	}

	public static void main(String[] args) throws IOException, ParseException {
		ClassificationWekaModel modelBuilder = new ClassificationWekaModel();
		modelBuilder.processInput(args);
		try {
			modelBuilder.createTrainData();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
