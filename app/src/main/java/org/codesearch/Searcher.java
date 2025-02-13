package org.codesearch;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.MMapDirectory;

import org.codesearch.Units.Unit;
import org.json.JSONObject;

public class Searcher {
    public static List<Unit> runQuery(Path indexPath, List<String> keys, boolean isFuzzy, int NumTopDocs) throws Exception {
        MMapDirectory dir = new MMapDirectory(indexPath);
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        for (String key: keys) {
            Term term = new Term("key", key);
            query.add(isFuzzy ? 
                new FuzzyQuery(term, (int)(key.length()*0.1)) : 
                new TermQuery(term), BooleanClause.Occur.MUST);
        }

        // System.out.println(searcher.collectionStatistics("key").toString());
        // System.out.println(searcher.count(new TermQuery(new Term("key", "import"))));

        TopDocs results = searcher.search(query.build(), NumTopDocs);
        List<Unit> res = new ArrayList<>();
        
        for (ScoreDoc scoreDoc: results.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            res.add(new Unit(
                doc.get("file"), 
                Integer.parseInt(doc.get("line")),
                new JSONObject(doc.get("json")),
                List.of(doc.getValues("keys")))
            );
        }

        return res;
    }
}
