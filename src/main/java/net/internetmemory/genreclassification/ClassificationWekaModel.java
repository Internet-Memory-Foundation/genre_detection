package net.internetmemory.genreclassification;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONArray;
import org.json.JSONObject;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.LibSVMSaver;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ClassificationWekaModel implements Serializable{
	private Booster xgbModel;
	private Classifier urlModel;
	private Booster metaModel;
	private List<String> nameStatisticsFeatures = new ArrayList<>();
	private JSONArray jsonArray;
	static Set<String> currencies;
	

	private boolean initialized = false;

	static final String modelFolder = "/home/jie/projects/GenreClassification/model/";

	static final List<String> classes = Arrays.asList("Forum", "News", "Blogs","Marketplace", "Spam", "Porn");

	private void initialize() throws IOException, ClassNotFoundException {
	    File annotation = new File("/home/jie/projects/GenreClassification/annotation_new.json");
	    currencies = (Set<String>) new ObjectInputStream(
				new FileInputStream("/home/jie/projects/GenreClassification/model/currency.set")).readObject();
		String content = FileUtils.readFileToString(annotation);
		jsonArray = new JSONArray(content);
		initialized = true;
	}

	private static Classifier buildTextClassifier(Instances trainData, Classifier baseClassifier) throws Exception {
		trainData.setClassIndex(trainData.numAttributes()-1);
		StringToWordVector filter = new StringToWordVector();
		filter.setInputFormat(trainData);
		filter.setLowerCaseTokens(true);
		filter.setIDFTransform(true);
		filter.setTFTransform(true);
		filter.setMinTermFreq(3);
		FilteredClassifier classifier = new FilteredClassifier();
		classifier.setFilter(filter);
		classifier.setClassifier(baseClassifier);
		classifier.buildClassifier(trainData);
		return classifier;
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

        //part1
		List<Triple<String, String, String>> dataset = new ArrayList<>();
        for(int i=0; i<listLabel.size(); i++){
        	if(!listLabel.get(i).equals("Other")){
				dataset.add(Triple.of(listPage.get(i), listUrl.get(i), listLabel.get(i)));
			}
        }
        Collections.shuffle(dataset);

		// production: change values
        List<Triple<String, String, String>> trainingset = dataset.subList(0, new Double(dataset.size()*0.75).intValue());
        List<Triple<String, String, String>> metaset = dataset.subList(new Double(dataset.size()*0.75).intValue(),
				dataset.size());
        List<Triple<String, String, String>> testset = dataset.subList(new Double(dataset.size()*0.8).intValue(), dataset.size());

		ClassificationBuildData trainData = new ClassificationBuildData(classes, null);
		trainData.buildTrainData(trainingset);
		writeData(trainData.getStatisticsData(), modelFolder+"training.libsvm");
		nameStatisticsFeatures = trainData.getNameStatisticsFeatures();
        Classifier classifier = new NaiveBayesMultinomial();
		urlModel = buildTextClassifier(trainData.getUrlData(), classifier);

		ClassificationBuildData metaData = new ClassificationBuildData(classes, nameStatisticsFeatures);
		metaData.buildEvalData(metaset);
		writeData(metaData.getStatisticsData(), modelFolder+"meta.libsvm");

		ClassificationBuildData testData = new ClassificationBuildData(classes, nameStatisticsFeatures);
		testData.buildEvalData(testset);
		writeData(testData.getStatisticsData(), modelFolder+"test.libsvm");

		DMatrix trainMat = new DMatrix(modelFolder+"training.libsvm");
		DMatrix metaMat = new DMatrix(modelFolder+"meta.libsvm");
		DMatrix testMat = new DMatrix(modelFolder+"test.libsvm");
		Map<String, Object> params = new Hashtable<String, Object>(){{
			put("nthread", 4);
			put("n_estimators", 3000);
			put("eval_metric", "merror");
			put("num_class", classes.size());
		}};
		Map<String, DMatrix> watchs = new Hashtable<String, DMatrix>(){{
			put("train", trainMat);
			put("test", testMat);
		}};
		Booster booster = XGBoost.train(trainMat, params, 50, watchs, null, null);

		// build meta classifier
		float[][] metaXGB = booster.predict(metaMat, true);
		List<double[]> metaLR = new ArrayList<>();
		for(Instance instance: metaData.getUrlData()){
			metaLR.add(urlModel.distributionForInstance(instance));
		}
		DMatrix metaTrainingMat = metaData.getMetaData(metaXGB, metaLR, metaData.getListLabel());
		Map<String, DMatrix> metaWatchs = new Hashtable<String, DMatrix>(){{
			put("train", metaTrainingMat);
		}};
		Map<String, Object> metaParams = new Hashtable<String, Object>(){{
			put("nthread", 4);
			put("n_estimators", 1000);
			put("eval_metric", "merror");
			put("num_class", classes.size());
		}};
		Booster metaBooster = XGBoost.train(metaTrainingMat, metaParams, 50, metaWatchs, null, null);

		//save model
		Object[] toSerialize = new Object[]{nameStatisticsFeatures, urlModel, booster, metaBooster, currencies};
		ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(modelFolder + "model.bin"));
		oout.writeObject(toSerialize);
		oout.flush();
		oout.close();

		// evaluate model
		float[][] resultXGB = booster.predict(testMat);
		float[][] resultProbaXGB = booster.predict(testMat, true);
        List<Double> groundTruth = new ArrayList<>();
        List<Double> resultLR = new ArrayList<>();
        List<double[]> resultProbaLR = new ArrayList<>();
        for(Instance instance: testData.getUrlData()){
			groundTruth.add(instance.classValue());
			resultLR.add(urlModel.classifyInstance(instance));
			resultProbaLR.add(urlModel.distributionForInstance(instance));
        }

		DMatrix metaTestMat = ClassificationBuildData.buildMetaData(resultProbaXGB, resultProbaLR, testData.getListLabel());
		float[][] resultMeta = metaBooster.predict(metaTestMat);

		List<String> correctXgb = new ArrayList<>();
		List<String> correctLR = new ArrayList<>();
		List<String> correctMeta = new ArrayList<>();
        for(int i=0; i<groundTruth.size(); i++){
        	int gt = groundTruth.get(i).intValue();
			if(gt == (int) resultXGB[i][0]){
            	correctXgb.add(classes.get(gt));
			}
			if(gt == resultLR.get(i).intValue()){
				correctLR.add(classes.get(gt));
			}
			if(gt == (int) resultMeta[i][0]){
				correctMeta.add(classes.get(gt));
			}
        }

        /* for debug
        for(int i=0; i<testset.size(); i++){
        	System.out.printf("%s\t%s\t%s\n", testset.get(i).getRight(), testset.get(i).getMiddle(), classes.get((int) resultXGB[i][0]));
		}*/

		Map<String, Long> clsGroundTruth = groundTruth.stream()
				.map(e -> classes.get(e.intValue()))
				.collect(Collectors.groupingBy(e -> e, Collectors.counting()));
		Map<String, Long> accXgb = correctXgb.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
		Map<String, Long> accLR = correctLR.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
		Map<String, Long> accMeta = correctMeta.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));

		System.out.printf("\n\nAcc Xgb:\t%.4f\tAcc LR:\t%.4f\tAcc Meta:\t%.4f\n\n",
				accXgb.values().stream().mapToDouble(Long::doubleValue).sum()/groundTruth.size(),
				accLR.values().stream().mapToDouble(Long::doubleValue).sum()/groundTruth.size(),
				accMeta.values().stream().mapToDouble(Long::doubleValue).sum()/groundTruth.size());
        for(String c: classes){
			double accx = accXgb.containsKey(c) ? accXgb.get(c).doubleValue()/clsGroundTruth.get(c) : 0;
			double accl = accLR.containsKey(c) ? accLR.get(c).doubleValue()/clsGroundTruth.get(c) : 0;
			double accm = accMeta.containsKey(c) ? accMeta.get(c).doubleValue()/clsGroundTruth.get(c) : 0;
			System.out.printf("%s\t%d:\nAcc Xgb:\t%.4f\tAcc LR:\t%.4f\tAcc Meta:\t%.4f\n",
					c, clsGroundTruth.get(c).intValue(), accx, accl, accm);
		}
	}
	
	private static void writeData(Instances instances, String fileName) throws IOException{
		LibSVMSaver saverStat = new LibSVMSaver();
		saverStat.setInstances(instances);
		saverStat.setFile(new File(fileName));
		saverStat.writeBatch();
	}


	void loadModel(String modelPath) throws IOException, ClassNotFoundException, XGBoostError {
		ObjectInput oin = new ObjectInputStream(new FileInputStream(modelPath));
		Object[] objects = (Object[]) oin.readObject();
		this.nameStatisticsFeatures = (List<String>) objects[0];
		this.urlModel = (Classifier) objects[1];
		this.xgbModel = (Booster) objects[2];
		this.metaModel = (Booster) objects[3];
		this.currencies = (Set<String>) objects[4];
	}

	
	String predict(String url) throws Exception {
		String html;
		InputStream in = new URL(url).openStream();
		try {
			html = IOUtils.toString(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
		return predict(html, url);
	}

	String predict(byte[] htmlBytes, String url) throws Exception {
		return predict(new String(htmlBytes), url);
	}

	String predict(String htmlString, String url) throws Exception {
		ClassificationBuildData build = new ClassificationBuildData(classes, nameStatisticsFeatures);
		List<Triple<String, String, String>> triple = Collections.singletonList(Triple.of(htmlString, url, null));
		build.buildTestData(triple);
		Instances statisticsData = build.getStatisticsData();
		Instances urlData = build.getUrlData();
		double[] featureDouble = statisticsData.firstInstance().toDoubleArray();
		float[] features = new float[featureDouble.length];
		features[0] = 0f;
		for(int i=1; i<featureDouble.length; i++){
			features[i] = (float) featureDouble[i-1];
		}
		DMatrix dmat = new DMatrix(features, 1, features.length);
		float[][] probaXGB = xgbModel.predict(dmat, true);
		List<double[]> probaBayes = Arrays.asList(urlModel.distributionForInstance(urlData.get(0)));
		DMatrix toPredict = ClassificationBuildData.buildTestMetaData(probaXGB, probaBayes);
		//float[][] predictionXGB = xgbModel.predict(dmat);
		//System.out.printf("Predicted by XGB as:\t%s\n", classes.get((int) predictionXGB[0][0]));
		//Double predictionBayes = urlModel.classifyInstance(urlData.get(0));
		//System.out.printf("Predicted by Bayes as:\t%s\n", classes.get(predictionBayes.intValue()));
		//System.out.printf("Predicted by Meta as:\t%s\n", classes.get((int) metaModel.predict(toPredict)[0][0]));
		String result = classes.get((int) metaModel.predict(toPredict)[0][0]);
		//System.out.println("URL: " + url);
		//System.out.printf("Predicted as:\t%s\n", result);
		return result;
	}
	
	public static void main(String[] args) throws IOException {
		ClassificationWekaModel modelBuilder = new ClassificationWekaModel();
		try {
			modelBuilder.createTrainData();
		} catch (Exception e) {			
			e.printStackTrace();
		}
	}
}
