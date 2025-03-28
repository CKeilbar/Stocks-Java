package main;

import java.util.*;


//This class keeps track of the optional tags for all entries
class TagTracker {
    private Map<String, Map<String, Integer>> tagCount;
    //The key is the tag name, the value is a map holding the value and the number of times that value is present

    public TagTracker(){
        tagCount = new HashMap<>();
    }

    //Call to add the set of tags
    public void addEntry(Set<Map.Entry<String, String>> tags){
        for(Map.Entry<String, String> i : tags){
            String tag = i.getKey();
            String value = i.getValue();
            
            if(tagCount.containsKey(tag)){//Update the count for this entry
                Map<String, Integer> thisTag = tagCount.get(tag);
                int valueToAdd = thisTag.getOrDefault(value, 0);
                thisTag.put(value, valueToAdd + 1);
            }
            else{//Add a new map for this new key
                Map<String, Integer> toInsert = new HashMap<>();
                toInsert.put(value, 1);
                tagCount.put(tag, toInsert);
            }
        }
    };

    //Call to remove the set of tags
    public void removeEntry(Set<Map.Entry<String, String>> tags){
        for(Map.Entry<String, String> i : tags){
            String tag = i.getKey();
            String value = i.getValue();
            
            Map<String, Integer> thisTag = tagCount.get(tag);
            int count = thisTag.get(value);
            if(count == 1){//Last instance of this value, remove the whole map
                thisTag.remove(value);
            }
            else{
                thisTag.put(value, count - 1);
            }
            if(thisTag.isEmpty()){//No more entries contain this tag
                tagCount.remove(tag);
            }
        }
    };

    //Returns all of the current values for the given tag
    public String[] getValuesForTag(String tag){
        if(tagCount.containsKey(tag)){
            return tagCount.get(tag).keySet().toArray(new String[0]);
        }
        else{
            return null;
        }
    };

    public String[] getTags(){
        return tagCount.keySet().toArray(new String[0]);
    };

    //Returns the largest number of values for any tag
    public int maxValsForTag(){
        int max = 0;
        for(Map<String, Integer> i : tagCount.values()){
            if(i.size() > max){
                max = i.size();
            }
        }
        return max;
    };
}

