package net.internetmemory.genreclassification;

/**
 * Created by jie on 21/06/17.
 */
public class TestClassifier {
    private static final String url = "http://blog.echen.me/2012/01/03/introduction-to-conditional-random-fields/";

    public static void main(String[] args) throws Exception {
        ClassificationWekaModel model = new ClassificationWekaModel();
        model.loadModel("/home/jie/projects/GenreClassification/model/model.bin");
        System.out.println(model.predict(url));
    }
}
