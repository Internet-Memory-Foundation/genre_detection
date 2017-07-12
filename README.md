# HTML page genre detection

In this project, you can find code to train and use the hybrid learner to detect predefined categories of webpages.

Requirements:
 1. Java 8
 2. Maven 3
 3. XGBOOST

The learner uses a combination of SVM and XGBOOST. The SVM comes from WEKA library and from maven central. The problematic requirement is the XGBOOST that is not a part of this distribution, because it contains platform dependent native libraries,
so it needs to be compiled from sources on the intended platform and made available in the local maven repository. 


### Compiling XGBOOST

1. checkout the whole package at https://github.com/dmlc/xgboost
2. make sure that you have intalled gcc at least of a version 5 (especially on Mac OS),
3. go to `xgboost/jvm-packages` and run `mvn install` (in case you have several gcc versions installed, you can make sure that the correct is used by using `export CXX=g++-5`)


### Compiling this project

Provided that you successfully compiled XGBOOST java libs and placed them in your local repo, the compilation of this project is simple.
If you intend to use this project as a library in a different project, doing simple mvn install will suffice. If you want to build a standalone jar that
will contain all the dependencies in order to use it as a commandline tool (for instance for examples listed below), use the `fatjar` profile: ` mvn -P fatjar clean package `.

### Predicting using the trained model

In the project you can find some version(s) of the already trained model. In order to use the model to predict a genre you can use a Predict class from the project, for example:

> java -cp target/genre-classification-1.0-SNAPSHOT.jar net.internetmemory.genreclassification.Predict -modelPath resources/model-2017-07-12/model.bin -url http://www.zeit.de


The method will fetch the URL from the live web, parse and make prediction. There is also an API `String predict(byte[] htmlBytes, String url)` if you want to use this library rather programmatically.


### Training a model

The model can be trained using a input in, for the moment, JSON, in this format:

`[ {  "pld": "www.forbes.com",
            "html": "", 
            "label": "Porn",
            "url": "http://turras.argentinasgratis.com.ar:80/" 
            }
            ]`

So it is a list of objects havving 4 properties (pld, html, label, url), wher all of them are of String type.

A requirement is the file with list of currencies, that can be found among the resources.

> java -Xmx4g -cp target/genre-classification-1.0-SNAPSHOT.jar net.internetmemory.genreclassification.ClassificationWekaModel -modelPath /tmp -trainingPath /tmp/annotation_new.json -currenciesPath resources/currency.set 

