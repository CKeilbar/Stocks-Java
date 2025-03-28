package main;

import java.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import javax.imageio.ImageIO;

public class Main {

    public static ArrayList<Entry> entries = new ArrayList<Entry>();
    private final String saveFname = "prevInfo.txt";
    private TagTracker tagMap = new TagTracker();
    private final DateTimeFormatter dispTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    private LocalDateTime priceTime = LocalDateTime.now(); //Default to current, overwritten if previous is found
    private LocalDateTime dbTime = priceTime;
    private String prevApiKey = "";

    //UI Stuff
    private JLabel dateLabel;
    JFrame frame = new JFrame("Stock Visualizer");
    EditPanel editPanel;
    JPanel editPane = new JPanel(new GridBagLayout());
    JPanel graphPane = new JPanel(new GridBagLayout());
    JPanel cfgPane = new JPanel(new GridBagLayout());
    static JTextField apiField = new JTextField(18); //Key is 16 columns, use 18 for space
    JTabbedPane mainPane = new JTabbedPane();

    public static void main(String[] args) {
        Main tempMain = new Main();
    }

    //Initializes the UI
    public Main(){
        readPrev();

        /*try{//Windows does not respect setting the button colour
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch(Exception unused){}//Too bad, default look it is*/

        try{
            frame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
        } catch(Exception unused){}//Too bad, no icon

        //Save before exit
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
                writePrev();
            };
        });

        //Set up UI
        drawAddPane();
        drawEditPane();
        drawGraphPane();
        drawCfgPane();

        mainPane.addTab("Add items", editPanel);
        mainPane.addTab("Edit items", editPane);
        mainPane.addTab("Graph items", graphPane);
        mainPane.addTab("Config", cfgPane);

        frame.add(mainPane);
        frame.pack();//Automatically sets the size, probably not optimal...
        frame.setVisible(true);
    }

    //Reads from the local file for our saved entries
    private void readPrev(){
        try(BufferedReader br = new BufferedReader(new FileReader(saveFname))){
            String line;
            //First line is the saved API key
            prevApiKey = br.readLine();
            //Next two lines are the last price modification and database modification dates
            try{
                line = br.readLine();
                priceTime = LocalDateTime.parse(line);
                line = br.readLine();
                dbTime = LocalDateTime.parse(line);
            } catch (DateTimeParseException unused){
                //Use initialized defaults
            }
            //Entries are in the following format:
            //name, quantity, updatePrice, price, [optional tag 1, optional value 1], ...
            while((line = br.readLine()) != null){
                String[] splitLine = line.split(",");
                Entry currentEntry = new Entry(splitLine[0], Float.parseFloat(splitLine[1]), "yes".equals(splitLine[2]), Float.parseFloat(splitLine[3]));
                for(int i = 4; i < splitLine.length; i += 2){
                    currentEntry.addValue(splitLine[i], splitLine[i+1]);
                }
                createEntry(currentEntry);
            }

        } catch (IOException unused){
            //Very bad
        }
    };

    //Writes to the local file to store our entries for next time
    private void writePrev(){
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(saveFname))){
            bw.write(apiField.getText());
            bw.newLine();
            bw.write(priceTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            bw.write(dbTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            bw.newLine();
            for(Entry i : entries){
                bw.write(i.saveableLine());
            }
        } catch (Exception unused){
            //Very bad
        }
    };

    private String datesToLabel(){
        String startString = "Database last modified ";
        String dbString = "";
        String middleString = "; prices last updated ";
        String priceString = "";

        try {
            dbString = dbTime.format(dispTimeFormat);
            priceString = priceTime.format(dispTimeFormat);
        } catch (DateTimeException unused){
            //Use the default empty strings
        }
        return startString + dbString + middleString + priceString;
    }

    //These functions deal with the tag manager as well as the list
    private void removeEntry(Entry entry){
        entries.remove(entry);
        tagMap.removeEntry(entry.getIterable());
    }
    private void createEntry(Entry entry){
        entries.add(entry);
        tagMap.addEntry(entry.getIterable());
    }

    //Convenience method for creating the constraints
    //Aditional manual configuration will be optionally needed to set the weights
    public static GridBagConstraints createGridBagConstraints(int xLoc, int xWidth, int yLoc, int yHeight){
        GridBagConstraints returnable = new GridBagConstraints();
        returnable.gridx = xLoc;
        returnable.gridwidth = xWidth;
        returnable.gridy = yLoc;
        returnable.gridheight = yHeight;
        return returnable;
    };

    //This panel is used to create new entries
    private void drawAddPane(){
        
        editPanel = new EditPanel(frame, tagMap);

        dateLabel = new JLabel(datesToLabel());
        GridBagConstraints dateLabelC = createGridBagConstraints(0, 1, 7, 1);
        editPanel.add(dateLabel, dateLabelC);

        JButton saveButton = new JButton("Create");
        saveButton.addActionListener(e -> {
            Entry created = editPanel.tryConstruct(apiField.getText(), false);
            if (created != null){
                createEntry(created);

                //Update UI
                dbTime = LocalDateTime.now();
                dateLabel.setText(datesToLabel());
                frame.getContentPane().validate();
                frame.getContentPane().repaint();
                //Both other panes need to be redrawn
                drawGraphPane();
                drawEditPane();
            }
        });

        //Button unique to this tab
        JButton updateButton = new JButton("Update all prices");
        updateButton.addActionListener(e -> {
            //Blocks others
            JDialog progressDialog = new JDialog(frame, "Updating prices...", Dialog.ModalityType.APPLICATION_MODAL);

            JProgressBar progressBar = new JProgressBar(0, Math.max(entries.size(), 1));
            progressBar.setValue(0);
            progressBar.setString("Initializing");
            progressBar.setStringPainted(true);

            PriceWorker priceWorker = new PriceWorker();
            priceWorker.addPropertyChangeListener(new PropertyChangeListener(){
                public void propertyChange(PropertyChangeEvent evt){
                    if(evt.getPropertyName() != "progress"){
                        return;
                    }
                    int progress = (Integer) evt.getNewValue();
                    progressBar.setValue(progress);
                    if(progress < entries.size()){
                        progressBar.setString(entries.get(progress).getTicker());
                    }
                    else{
                        progressBar.setString("Done");
                        try{
                            ArrayList<String> result = priceWorker.get();
                            boolean resultPassed = result.size() == 0;
                            String msg = "";
                            if(resultPassed){
                                msg = "All prices updated successfully.";
                            }
                            else{
                                msg = "The following tickers failed to update: " + String.join(", ", result);
                            }
                            JOptionPane.showMessageDialog(frame, msg, (resultPassed ? "Price update successful" : "Price update unsuccessful"), (resultPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
                        } catch(Exception unused){
                            JOptionPane.showMessageDialog(frame, "Reading progress returned an exception.", "Price update unknown", JOptionPane.ERROR_MESSAGE);
                        }

                        priceTime = LocalDateTime.now();
                        dateLabel.setText(datesToLabel());
                        drawEditPane();
                        progressDialog.dispose();
                    }
                };
            });
            progressDialog.add(progressBar);
            priceWorker.execute();
            progressDialog.pack();
            progressDialog.setVisible(true);
        });

        //Panel for the create/update buttons
        JPanel buttonPanel = new JPanel(new GridLayout(0, 2));
        buttonPanel.add(updateButton);
        buttonPanel.add(saveButton);

        GridBagConstraints buttonPanelC = createGridBagConstraints(1, 1, 7, 1);
        buttonPanelC.anchor = GridBagConstraints.SOUTHEAST;
        editPanel.add(buttonPanel, buttonPanelC);
    }

    //Searches through the entries to find the ones satisfying the criteria
    //The inc values MUST be present, and the rem values MUST NOT, this functions as a logical AND
    private Map<String, Float> findGraphables(String axis, ArrayList<String> incVal, ArrayList<String> incTag, ArrayList<String> remVal, ArrayList<String> remTag){
        ArrayList<Entry> clone = new ArrayList<>(entries);

        for(int i = 0; i < incVal.size(); i++){//Remove all of the entries that don't satisfy the includes
            for(int j = clone.size()-1; j >= 0; j--){
                if(!clone.get(j).containsPair(incTag.get(i), incVal.get(i))){
                    clone.remove(j);
                }
            }
            if(clone.size() == 0){
                return null;
            }
        }
        for(int i = 0; i < remVal.size(); i++){//Remove all entries that satisfy the removes
            for(int j = clone.size()-1; j >= 0; j--){
                if(clone.get(j).containsPair(remTag.get(i), remVal.get(i))){
                    clone.remove(j);
                }
            }
            if(clone.size() == 0){
                return null;
            }
        }
        //Clone now holds only valid values
        Map<String, Float> unsortedMap = new HashMap<>();
        for(Entry i : clone){
            String myTag = "".equals(axis) ? i.getTicker() : i.valueForTag(axis); //If no axis, use ticker as label
            unsortedMap.put(myTag, i.getValue() + unsortedMap.getOrDefault(myTag, 0f));
        }

        Map<String, Float> retMap = new LinkedHashMap<>();//Order is required
        //Sort by value
        unsortedMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEach(entry -> retMap.put(entry.getKey(), entry.getValue()));
        return retMap;
    }

    //Draws the pane used for graphing in addition to the one that holds the graph
    private void drawGraphPane(){
        graphPane.removeAll();

        JPanel graphPaneTag = new JPanel(new GridLayout(0, tagMap.maxValsForTag() + 1));

        JScrollPane graphScrollPane = new JScrollPane(graphPaneTag);
        GridBagConstraints graphScrollPaneC = createGridBagConstraints(0, 2, 0, 1);
        graphScrollPaneC.weightx = 0.5;
        graphScrollPaneC.weighty = 0.5;
        graphScrollPaneC.fill = GridBagConstraints.BOTH;
        graphPane.add(graphScrollPane, graphScrollPaneC);

        JButton graphButton = new JButton("Graph");

        //Launch the graph
        graphButton.addActionListener(e -> {
            int length = tagMap.maxValsForTag()+1;
            ArrayList<String> includeValues = new ArrayList<>();
            ArrayList<String> includeValueTags = new ArrayList<>();
            ArrayList<String> removeValues = new ArrayList<>();
            ArrayList<String> removeValueTags = new ArrayList<>();
            String axisTag = "";

            //Look at the buttons to see what has been selected
            for(int i = 0; i < tagMap.getTags().length * length; i++){
                JButton buttonToExamine = (JButton) graphPaneTag.getComponent(i);
                Color buttonColour = buttonToExamine.getBackground();
                if(i % length == 0 && buttonColour == Color.GREEN){
                    axisTag = buttonToExamine.getText();
                }
                else if(buttonColour == Color.GREEN){//Includes
                    includeValues.add(buttonToExamine.getText());
                    includeValueTags.add(((JButton) graphPaneTag.getComponent(i - i % length)).getText());
                }
                else if(buttonColour == Color.RED){//Excludes
                    removeValues.add(buttonToExamine.getText());
                    removeValueTags.add(((JButton) graphPaneTag.getComponent(i - i % length)).getText());
                }
                else{
                }
            }
            Map<String, Float> graphable = findGraphables(axisTag, includeValues, includeValueTags, removeValues, removeValueTags);
            if(graphable == null){
                JOptionPane.showMessageDialog(frame, "No entries matched the criteria.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            else{//Valid graph
                JFrame graphFrame = new JFrame("Pie graph");
                try{
                    graphFrame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
                } catch(Exception unused){}//Too bad, no icon

                graphFrame.setPreferredSize(new Dimension(600, 600));

                JPanel piePane = new JPanel(new GridBagLayout());

                JLabel graphTitle = new JLabel(axisTag);
                GridBagConstraints graphTitleC = createGridBagConstraints(0, 1, 0, 1);
                piePane.add(graphTitle, graphTitleC);

                Pie myPie = new Pie(graphable);
                GridBagConstraints myPieC = createGridBagConstraints(0, 1, 1, 1);
                myPieC.fill = GridBagConstraints.BOTH;
                myPieC.weightx = 0.5;
                myPieC.weighty = 0.9;
                piePane.add(myPie, myPieC);

                JPanel legendPanel = new JPanel();
                GridBagConstraints legendPanelC = createGridBagConstraints(0, 1, 2, 1);
                legendPanelC.fill = GridBagConstraints.BOTH;
                legendPanelC.weightx = 0.5;
                legendPanelC.weighty = 0.1;

                int[] j = {0};//Using an array means it doesn't have to be atomic for some reason
                ArrayList<Color> colourList = myPie.getColours();
                float total = myPie.getTotal();
                graphable.forEach((k, v) -> {
                    JLabel legendLabel = new JLabel(String.format("%s $%.2f %.0f%% ", k, v, v/total*100f));
                    legendLabel.setForeground(colourList.get(j[0]));
                    legendPanel.add(legendLabel);
                    j[0]++;
                });
                piePane.add(legendPanel, legendPanelC);

                JButton closeButton = new JButton("Close");
                closeButton.addActionListener(lamb -> {
                        graphFrame.dispose();
                });
                GridBagConstraints closeButtonC = createGridBagConstraints(0, 1, 3, 1);
                closeButtonC.anchor = GridBagConstraints.SOUTHEAST;
                piePane.add(closeButton, closeButtonC);

                graphFrame.add(piePane);
                graphFrame.pack();
                graphFrame.setVisible(true);
            }
        });

        GridBagConstraints graphButtonC = createGridBagConstraints(1, 1, 1, 1);
        graphButtonC.anchor = GridBagConstraints.SOUTHEAST;
        graphPane.add(graphButton, graphButtonC);

        JLabel instructionsLabel = new JLabel("Use the left column to select the tag that is displayed on the graph. Use the other columns to filter out entries.");
        GridBagConstraints instructionsLabelC = createGridBagConstraints(0, 1, 1, 1);
        instructionsLabelC.anchor = GridBagConstraints.SOUTHWEST;
        instructionsLabelC.weightx = 0.5;
        graphPane.add(instructionsLabel, instructionsLabelC);

        //Rows of tags, colomuns of values
        int widestRow = tagMap.maxValsForTag();
        String[] tags = tagMap.getTags();
        for(int i = 0; i < tags.length; i++){
            JButton tagNameButton = new JButton(tags[i]);
            tagNameButton.addActionListener(e -> {
                Color currentColor = tagNameButton.getBackground();
                //Toggle off all other buttons
                for(int j = 0; j < tags.length; j++){
                    Component buttonToExamine = graphPaneTag.getComponent(j*(widestRow+1));
                    buttonToExamine.setBackground(UIManager.getColor("Button.background"));
                }
                //Set my background
                if(currentColor != Color.GREEN){
                    tagNameButton.setBackground(Color.GREEN);
                }
            });
            graphPaneTag.add(tagNameButton);

            String[] values = tagMap.getValuesForTag(tags[i]);
            int valuesInThisRow = values.length;
            for(int j = 0; j < widestRow; j++){
                if(j < valuesInThisRow){
                    JButton valueButton = new JButton(values[j]);
                    valueButton.addActionListener(e -> {
                        Color currentColor = valueButton.getBackground();
                        if(currentColor == Color.GREEN){
                            valueButton.setBackground(Color.RED);
                        }
                        else if(currentColor == Color.RED){
                            valueButton.setBackground(UIManager.getColor("Button.background"));
                        }
                        else{
                            valueButton.setBackground(Color.GREEN);
                        }
                    });
                    graphPaneTag.add(valueButton);
                }
                else{
                    //invisible, reserve space
                    JButton invisibleItem = new JButton("");
                    invisibleItem.setVisible(false);
                    graphPaneTag.add(invisibleItem);
                }
            }
        }
    };

    //Basically the exact same as the edit pane, they should be merged in the future
    private void drawEditPane(){
        editPane.removeAll();
        JPanel editTagPane = new JPanel(new GridBagLayout());
        int numRows = entries.size();
        for(int i = 0; i < numRows; i++){
            Entry entryToModify = entries.get(i);

            JButton deleteButton = new JButton("Remove");
            deleteButton.addActionListener(e -> {
                removeEntry(entryToModify);
                dbTime = LocalDateTime.now();
                dateLabel.setText(datesToLabel());
                drawGraphPane();
                drawEditPane();
            });

            GridBagConstraints deleteButtonC = createGridBagConstraints(0, 1, i, 1);
            deleteButtonC.fill = GridBagConstraints.HORIZONTAL;
            deleteButtonC.weightx = 0.1;
            editTagPane.add(deleteButton, deleteButtonC);

            JButton modifyButton = new JButton("Modify");
            modifyButton.addActionListener(new ActionListener(){
                @Override
                public void actionPerformed(ActionEvent e){
                    JFrame editFrame = new JFrame("Edit entry");
                    try{
                        editFrame.setIconImage(ImageIO.read(this.getClass().getResource("icon.png")));
                    } catch(Exception unused){}//Too bad, no icon

                    EditPanel localPane = new EditPanel(frame, tagMap, entryToModify);

                    JButton saveButton = new JButton("Modify");
                    saveButton.addActionListener(new ActionListener(){
                        @Override
                        public void actionPerformed(ActionEvent e){
                            Entry created = localPane.tryConstruct(apiField.getText(), false);
                            if (created != null){
                                removeEntry(entryToModify);
                                createEntry(created);
                                editFrame.dispose();
                                dbTime = LocalDateTime.now();
                                dateLabel.setText(datesToLabel());
                                drawEditPane();
                                drawGraphPane();
                            }
                        };
                    });
                    GridBagConstraints saveButtonC = createGridBagConstraints(1, 1, 7, 1);
                    saveButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    localPane.add(saveButton, saveButtonC);

                    JButton cancelButton = new JButton("Cancel");
                    cancelButton.addActionListener(lamb -> {
                        editFrame.dispose();
                    });
                    GridBagConstraints cancelButtonC = createGridBagConstraints(0, 1, 7, 1);
                    cancelButtonC.weightx = 1;
                    cancelButtonC.anchor = GridBagConstraints.SOUTHEAST;
                    localPane.add(cancelButton, cancelButtonC);

                    editFrame.add(localPane);
                    editFrame.pack();
                    editFrame.setVisible(true);
                };
            });
            GridBagConstraints modifyButtonC = createGridBagConstraints(1, 1, i, 1);
            modifyButtonC.fill = GridBagConstraints.HORIZONTAL;
            modifyButtonC.weightx = 0.1;
            editTagPane.add(modifyButton, modifyButtonC);

            JLabel descriptionLabel = new JLabel(entries.get(i).displayLine());
            GridBagConstraints descriptionLabelC = createGridBagConstraints(2, 1, i, 1);
            descriptionLabelC.weightx = 0.9;
            descriptionLabelC.fill = GridBagConstraints.HORIZONTAL;
            descriptionLabelC.insets = new Insets(5, 5, 5, 5);
            editTagPane.add(descriptionLabel, descriptionLabelC);
        }

        JScrollPane editScrollPane = new JScrollPane(editTagPane);
        GridBagConstraints editScrollPaneC = createGridBagConstraints(0, 1, 0, 1);
        editScrollPaneC.weightx = 0.5;
        editScrollPaneC.weighty = 0.5;
        editScrollPaneC.fill = GridBagConstraints.BOTH;
        editPane.add(editScrollPane, editScrollPaneC);
    };

    //Only holds the API key at present
    private void drawCfgPane(){
        cfgPane.removeAll();

        JLabel apiLabel = new JLabel("Alpha Vantage API Key");
        GridBagConstraints apiLabelC = createGridBagConstraints(0, 1, 0, 1);
        cfgPane.add(apiLabel, apiLabelC);

        apiField.setText(prevApiKey);
        GridBagConstraints apiFieldC = createGridBagConstraints(1, 1, 0, 1);
        cfgPane.add(apiField, apiFieldC);
    };

}
