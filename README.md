# HTML page genre detection

In this project, you can find code to train and use the learner to detect predefined categories of webpages.

Requirements:
 1. Java 8
 2. Maven 3

The learner uses Random Forest. The Random Forest classifier comes from WEKA library and from maven central. 

### Compiling this project

The compilation of this project is simple.
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

