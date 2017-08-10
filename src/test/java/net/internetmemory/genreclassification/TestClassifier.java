package net.internetmemory.genreclassification;

/**
 * Created by jie on 21/06/17.
 */
public class TestClassifier {
    private static final String url = "http://4porn.com/fr/89/french/";

    public static void main(String[] args) throws Exception {
        ClassificationWekaModel model = new ClassificationWekaModel();
        model.loadModel("/home/jie/projects/GenreClassification/model/model.bin");
        model.predict(url);
    }
}
