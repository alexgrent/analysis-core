package org.reactome.server.analysis.core.model;

import org.reactome.server.analysis.core.model.identifier.Identifier;
import org.reactome.server.analysis.core.model.identifier.InteractorIdentifier;
import org.reactome.server.analysis.core.model.identifier.MainIdentifier;
import org.reactome.server.analysis.core.model.identifier.OtherIdentifier;
import org.reactome.server.analysis.core.model.resource.MainResource;
import org.reactome.server.analysis.core.model.resource.ResourceFactory;
import org.reactome.server.analysis.core.result.external.*;
import org.reactome.server.analysis.core.util.MapSet;
import org.reactome.server.analysis.core.util.MathUtilities;

import java.util.*;

/**
 * For each pathway, this class contains the result of the analysis. There are three main parts in
 * this class: (1) the mapping between identifiers provided by the user and main identifiers used
 * for the result, (2) The set of reactions identifiers found for each main resource and (3) the
 * result counters -and statistics- for each main resource and for the combination of all of them
 *
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 */
public class PathwayNodeData {
    //Please note that counter is used for each main identifier and for the combinedResult
    class Counter {

        Counter() { }

        Counter(ExternalStatistics counter){
            this.totalEntities = counter.getEntitiesCount();
            this.foundEntities = counter.getEntitiesFound();
            this.entitiesRatio = counter.getEntitiesRatio();
            this.entitiesPValue = counter.getEntitiesPValue();
            this.entitiesFDR = counter.getEntitiesFDR();

            this.totalInteractors = counter.getInteractorsCount();
            this.foundInteractors = counter.getInteractorsFound();
            this.interactorsRatio = counter.getInteractorsRatio();

            this.totalFound = counter.getEntitiesAndInteractorsCount();

            this.totalReactions = counter.getReactionsCount();
            this.foundReactions = counter.getReactionsFound();
            this.reactionsRatio = counter.getReactionsRatio();
        }

        Integer totalEntities = 0; //Pre-calculated in setCounters method
        Integer foundEntities = 0;
        Double entitiesRatio;
        Double entitiesPValue;
        Double entitiesFDR;

        Integer totalInteractors = 0; //Pre-calculated in setCounters method
        Integer foundInteractors = 0;
        Double interactorsRatio;

        //Aggregation
        Integer totalFound = 0;

        //TP-Based analysis
        Integer totalReactions = 0; //Pre-calculated in setCounters method
        Integer foundReactions = 0;
        Double reactionsRatio;
    }

    private MapSet<MainResource, String> foundTotal = new MapSet<>();

    /*
    The following structure plays two different roles
    1) While building the data structure it will keep track of the contained physical entities in the pathway
    2) During the analysis it will keep track of the seen elements
    */
    private MapSet<Identifier, MainIdentifier> entities = new MapSet<>();

    /*
    The following structure plays two different roles
    1) While building the data structure it will keep track of the contained reactions in the pathway
    2) During the analysis it will keep track of the seen reactions
    */
    private MapSet<MainResource, AnalysisReaction> reactions = new MapSet<>();

    /*
    The following structure plays two different roles
    1) While building the data structure it will keep track of the contained physical entities in the pathway
    2) During the analysis it will keep track of the seen elements
    */
    private MapSet<MainIdentifier, InteractorIdentifier> interactors = new MapSet<>();

    //Analysis result containers
    private Map<MainResource, Counter> entitiesResult = new HashMap<>();
    private Counter combinedResult = new Counter();  //All main identifiers combined in one result

    public PathwayNodeData() {
    }

    public PathwayNodeData(ExternalPathwayNodeData data) {
        //statistics
        for (ExternalStatistics statistics : data.getStatistics()) {
            if ("TOTAL".equals(statistics.getResource())) {
                this.combinedResult = new Counter(statistics);
            } else {
                MainResource mr = ResourceFactory.getMainResource(statistics.getResource());
                this.entitiesResult.put(mr, new Counter(statistics));
            }
        }

        //entities
        for (ExternalIdentifier entity : data.getEntities()) {
            AnalysisIdentifier ai = new AnalysisIdentifier(entity);
            for (ExternalMainIdentifier emi : entity.getMapsTo()) {
                MainIdentifier mi = new MainIdentifier(emi, entity.getExp());
                OtherIdentifier otherIdentifier = new OtherIdentifier(mi.getResource(), ai);
                addEntity(otherIdentifier, mi);
            }
        }

        //interactors
        if (data.getInteractors() != null) {
            for (ExternalInteractor interactor : data.getInteractors()) {
                AnalysisIdentifier ai = new AnalysisIdentifier(interactor.getId(), interactor.getExp());
                for (ExternalInteraction interaction : interactor.getMapsTo()) {
                    for (ExternalMainIdentifier emi : interaction.getInteractsWith()) {
                        MainIdentifier mi = new MainIdentifier(emi, interactor.getExp());
                        InteractorIdentifier ii = new InteractorIdentifier(ai, interaction.getId());
                        addInteractors(mi, ii);
                    }
                }
            }
        }

        //reactions
        for (ExternalAnalysisReaction reaction : data.getReactions()) {
            for (String resource : reaction.getResources()) {
                MainResource mr = ResourceFactory.getMainResource(resource);
                this.reactions.add(mr, new AnalysisReaction(reaction));
            }
        }
    }

    public void addEntity(Identifier identifier, MainIdentifier mainIdentifier) {
        this.entities.add(identifier, mainIdentifier);
        this.foundTotal.add(mainIdentifier.getResource(), mainIdentifier.getValue().getId());
    }

    public void addInteractors(MainIdentifier mainIdentifier, InteractorIdentifier identifier) {
        this.interactors.add(mainIdentifier, identifier);
        this.foundTotal.add(mainIdentifier.getResource(), identifier.getMapsTo());
    }

    public void addReactions(MainResource mainResource, Set<AnalysisReaction> reactions) {
        this.reactions.add(mainResource, reactions);
    }


    public Integer getEntitiesAndInteractorsCount(){
        return combinedResult.totalFound;
    }

    public Integer getEntitiesAndInteractorsCount(MainResource resource){
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.totalFound;
        }
        return 0;
    }

    public Integer getEntitiesAndInteractorsFound() {
        int total = 0;
        for (MainResource resource : foundTotal.keySet()) {
            total += foundTotal.getElements(resource).size();
        }
        return total;
    }

    public Integer getEntitiesAndInteractorsFound(MainResource resource) {
        Set<String> found = this.foundTotal.getElements(resource);
        return found == null ? 0 : found.size();
    }

    // ENTITIES Result

    public Set<AnalysisIdentifier> getFoundEntities() {
        Set<AnalysisIdentifier> rtn = new HashSet<>();
        for (Identifier identifier : entities.keySet()) {
            for (MainIdentifier mainIdentifier : this.entities.getElements(identifier)) {
                rtn.add(mainIdentifier.getValue());
            }
        }
        return rtn;
    }

    public List<ExternalIdentifier> getExternalEntities(){
        List<ExternalIdentifier> rtn = new ArrayList<>();

        for (Identifier identifier : entities.keySet()) {
            ExternalIdentifier ei =  new ExternalIdentifier(identifier.getValue());
            for (MainIdentifier mainIdentifier : entities.getElements(identifier)) {
                ExternalMainIdentifier emi = new ExternalMainIdentifier(mainIdentifier);
                ei.addMapsTo(emi);
            }
            rtn.add(ei);
        }
        return rtn;
    }

    public Set<AnalysisIdentifier> getFoundEntities(MainResource resource) {
        Set<AnalysisIdentifier> rtn = new HashSet<>();
        for (Identifier identifier : entities.keySet()) {
            for (MainIdentifier mainIdentifier : this.entities.getElements(identifier)) {
                if (mainIdentifier.getResource().equals(resource)) {
                    rtn.add(mainIdentifier.getValue());
                }
            }
        }
        return rtn;
    }

    private List<List<Double>> groupEntitiesExpressionValues(Collection<AnalysisIdentifier> identifiers, Collection<InteractorIdentifier> interactors) {
        List<List<Double>> evss = new LinkedList<>();
        Collection<AnalysisIdentifier> aggregation = new HashSet<>();
        aggregation.addAll(identifiers);
        aggregation.addAll(interactors);
        for (AnalysisIdentifier identifier : aggregation) {
            int i = 0;
            for (Double ev : identifier.getExp()) {
                List<Double> evs;
                if (evss.size() > i) {
                    evs = evss.get(i);
                } else {
                    evs = new LinkedList<>();
                    evss.add(i, evs);
                }
                evs.add(ev);
                i++;
            }
        }
        return evss;
    }

    private List<Double> calculateAverage(List<List<Double>> expressionValues) {
        List<Double> avg = new LinkedList<>();
        int i = 0;
        for (List<Double> evs : expressionValues) {
            Double sum = 0.0;
            Double total = 0.0;
            for (Double ev : evs) {
                if (ev != null) {
                    sum += ev;
                    total++;
                }
            }
            if (total > 0.0) {
                avg.add(i++, sum / total);
            } else {
                avg.add(i++, null);
            }
        }
        return avg;
    }

    private List<AnalysisIdentifier> getEntitiesDuplication() {
        List<AnalysisIdentifier> rtn = new LinkedList<>();
        for (Identifier identifier : entities.keySet()) {
            for (MainIdentifier mainIdentifier : entities.getElements(identifier)) {
                rtn.add(mainIdentifier.getValue());
            }
        }
        return rtn;
    }

    private List<AnalysisIdentifier> getEntitiesDuplication(MainResource resource) {
        List<AnalysisIdentifier> rtn = new LinkedList<>();
        for (Identifier identifier : entities.keySet()) {
            for (MainIdentifier mainIdentifier : entities.getElements(identifier)) {
                if (mainIdentifier.getResource().equals(resource)) {
                    rtn.add(mainIdentifier.getValue());
                }
            }
        }
        return rtn;
    }

    private List<InteractorIdentifier> getInteractorsDuplication() {
        List<InteractorIdentifier> rtn = new LinkedList<>();
        for (MainIdentifier mainIdentifier : interactors.keySet()) {
            for (InteractorIdentifier identifier : interactors.getElements(mainIdentifier)) {
                rtn.add(identifier);
            }
        }
        return rtn;
    }

    private List<InteractorIdentifier> getInteractorsDuplication(MainResource resource) {
        List<InteractorIdentifier> rtn = new LinkedList<>();
        for (MainIdentifier mainIdentifier : interactors.keySet()) {
            if (mainIdentifier.getResource().equals(resource)) {
                for (InteractorIdentifier identifier : interactors.getElements(mainIdentifier)) {
                    rtn.add(identifier);
                }
            }
        }
        return rtn;
    }

    public List<Double> getExpressionValuesAvg() {
        return calculateAverage(groupEntitiesExpressionValues(getEntitiesDuplication(), getInteractorsDuplication()));
    }

    public List<Double> getExpressionValuesAvg(MainResource resource) {
        return calculateAverage(groupEntitiesExpressionValues(getEntitiesDuplication(resource), getInteractorsDuplication(resource)));
    }

    public Integer getEntitiesCount() {
        return this.combinedResult.totalEntities;
    }

    public Integer getEntitiesCount(MainResource resource) {
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.totalEntities;
        }
        return 0;
    }

    public Integer getEntitiesFound() {
        return getFoundEntities().size();
    }

    public Integer getEntitiesFound(MainResource resource) {
        return getFoundEntities(resource).size();
    }

    public Double getEntitiesPValue() {
        return this.combinedResult.entitiesPValue;
    }

    public Double getEntitiesPValue(MainResource resource) {
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.entitiesPValue;
        }
        return null;
    }

    public Double getEntitiesFDR() {
        return this.combinedResult.entitiesFDR;
    }

    public Double getEntitiesFDR(MainResource resource) {
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.entitiesFDR;
        }
        return null;
    }

    public Double getEntitiesRatio() {
        return this.combinedResult.entitiesRatio;
    }

    public Double getEntitiesRatio(MainResource resource) {
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.entitiesRatio;
        }
        return null;
    }

    public Double getInteractorsRatio() {
        return this.combinedResult.interactorsRatio;
    }

    public Double getInteractorsRatio(MainResource resource) {
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.interactorsRatio;
        }
        return null;
    }

    //TODO: Provide with the pathway identifiers mapping to main identifiers
    public MapSet<Identifier, MainIdentifier> getIdentifierMap() {
        return entities;
    }


    public MapSet<MainIdentifier, InteractorIdentifier> getInteractorMap() {
        return interactors;
    }

    // INTERACTORS Result

    public Set<InteractorIdentifier> getFoundInteractors(){
        return interactors.values();
    }

    public Set<InteractorIdentifier> getFoundInteractors(MainResource resource){
        Set<InteractorIdentifier> rtn = new HashSet<>();
        for (MainIdentifier mainIdentifier : interactors.keySet()) {
            if (mainIdentifier.getResource().equals(resource)) {
                rtn.addAll(interactors.getElements(mainIdentifier));
            }
        }
        return rtn;
    }

    public Integer getInteractorsCount(){
        return this.combinedResult.totalInteractors;
    }

    public Integer getInteractorsCount(MainResource resource){
        Counter counter = this.entitiesResult.get(resource);
        if (counter != null) {
            return counter.totalInteractors;
        }
        return 0;
    }

    public Integer getInteractorsFound(){
        Set<String> mapsTo = new HashSet<>();
        for (InteractorIdentifier interactor : getFoundInteractors()) {
            mapsTo.add(interactor.getMapsTo());
        }
        return mapsTo.size();
    }

    public Integer getInteractorsFound(MainResource resource){
        Set<String> mapsTo = new HashSet<>();
        for (InteractorIdentifier interactor : getFoundInteractors(resource)) {
            mapsTo.add(interactor.getMapsTo());
        }
        return mapsTo.size();
    }

    public List<ExternalInteractor> getExternalInteractors() {
        Map<String, ExternalInteractor> identifierMap = new HashMap<>();
        Map<String, ExternalInteraction> interactionMap = new HashMap<>();
        for (MainIdentifier mi : interactors.keySet()) {
            ExternalMainIdentifier emi = new ExternalMainIdentifier(mi);
            for (InteractorIdentifier interactor : interactors.getElements(mi)) {
                String id = interactor.getId();
                ExternalInteractor ei = identifierMap.getOrDefault(id, new ExternalInteractor(interactor));
                identifierMap.put(id, ei);

                String mapsTo = interactor.getMapsTo();
                ExternalInteraction ein = interactionMap.getOrDefault(mapsTo, new ExternalInteraction(mapsTo));
                interactionMap.put(mapsTo, ein);

                ein.addExternalMainIdentifier(emi);
                ei.addMapsTo(ein);
            }
        }
        return new ArrayList<>(identifierMap.values());
    }


    // REACTIONS Result

    public Set<AnalysisReaction> getReactions() {
        return reactions.values();
    }

    public Set<AnalysisReaction> getReactions(MainResource resource) {
        Set<AnalysisReaction> rtn = reactions.getElements(resource);
        if(rtn==null){
            rtn = new HashSet<>();
        }
        return rtn;
    }

    public List<ExternalAnalysisReaction> getExternalReactions() {
        Map<Long, ExternalAnalysisReaction> map = new HashMap<>();
        for (MainResource mr : reactions.keySet()) {
            for (AnalysisReaction rxn : reactions.getElements(mr)) {
                ExternalAnalysisReaction erxn = map.getOrDefault(rxn.getDbId(), new ExternalAnalysisReaction(rxn));
                map.put(rxn.getDbId(), erxn);
                erxn.add(mr.getName());
            }
        }
        return new ArrayList<>(map.values());
    }

    public Integer getReactionsCount() {
        return this.combinedResult.totalReactions;
    }

    public Integer getReactionsCount(MainResource mainResource) {
        Counter counter = this.entitiesResult.get(mainResource);
        if(counter!=null){
            return counter.totalReactions;
        }
        return 0;
    }

    public Integer getInteractorsReactionsCount() {
        return this.combinedResult.totalReactions;
    }

    public Integer getInteractorsReactionsCount(MainResource mainResource) {
        Counter counter = this.entitiesResult.get(mainResource);
        if(counter!=null){
            return counter.totalReactions;
        }
        return 0;
    }

    public Integer getReactionsFound(){
        return this.getReactions().size();
    }

    public Integer getReactionsFound(MainResource resource){
        return this.getReactions(resource).size();
    }

    public Double getReactionsRatio(){
        return this.combinedResult.reactionsRatio;
    }

    public Double getReactionsRatio(MainResource resource){
        Counter counter = this.entitiesResult.get(resource);
        if(counter !=null){
            return counter.reactionsRatio;
        }
        return null;
    }

    public Set<MainResource> getResources(){
        return this.entitiesResult.keySet();
    }

    public boolean hasResult(){
        return !foundTotal.isEmpty();
//        return !entities.isEmpty() || !interactors.isEmpty();
    }

    public void setEntitiesFDR(Double fdr){
        this.combinedResult.entitiesFDR = fdr;
    }

    public void setEntitiesFDR(MainResource resource, Double fdr){
        this.entitiesResult.get(resource).entitiesFDR = fdr;
    }

    //This is only called in build time
    void setCounters(PathwayNodeData speciesData){
        Set<AnalysisReaction> totalReactions = new HashSet<>();
        for (MainResource mainResource : reactions.keySet()) {
            Counter counter = getOrCreateCounter(mainResource);
            counter.totalReactions = reactions.getElements(mainResource).size();
            totalReactions.addAll(reactions.getElements(mainResource));
            counter.reactionsRatio = counter.totalReactions/speciesData.getReactionsCount(mainResource).doubleValue();
        }
        combinedResult.totalReactions += totalReactions.size(); totalReactions.clear();
        combinedResult.reactionsRatio =  combinedResult.totalReactions /speciesData.getReactionsCount().doubleValue();
        reactions = new MapSet<>();

        MapSet<MainResource, AnalysisIdentifier> aux = new MapSet<>();
        for (Identifier identifier : entities.keySet()) {
            for (MainIdentifier mainIdentifier : entities.getElements(identifier)) {
                aux.add(mainIdentifier.getResource(), mainIdentifier.getValue());
            }
        }
        for (MainResource mainResource : aux.keySet()) {
            Counter counter = this.getOrCreateCounter(mainResource);
            counter.totalEntities = aux.getElements(mainResource).size();
            counter.entitiesRatio =  counter.totalEntities/speciesData.getEntitiesCount(mainResource).doubleValue();
            counter.totalFound = getEntitiesAndInteractorsFound(mainResource);
            combinedResult.totalEntities += counter.totalEntities;
        }
        combinedResult.entitiesRatio = this.combinedResult.totalEntities / speciesData.getEntitiesCount().doubleValue();
        combinedResult.totalFound = getEntitiesAndInteractorsFound();
        entities = new MapSet<>();


        //INTERACTORS
        MapSet<MainResource, InteractorIdentifier> temp = new MapSet<>();
        //To ensure the main resource is present, we take into account the resource of the molecule PRESENT in the diagram
        for (MainIdentifier mainIdentifier : interactors.keySet()) {
            for (InteractorIdentifier identifier : interactors.getElements(mainIdentifier)) {
                temp.add(mainIdentifier.getResource(), identifier);
            }
        }
        interactors = new MapSet<>();

        for (MainResource mainResource : aux.keySet()) {
            Counter counter = this.getOrCreateCounter(mainResource);
            Set<InteractorIdentifier> interactors = temp.getElements(mainResource);
            counter.totalInteractors = interactors == null ? 0 : interactors.size();
            counter.interactorsRatio = counter.totalFound / speciesData.getEntitiesAndInteractorsCount(mainResource).doubleValue();
            if(counter.interactorsRatio.isNaN() || counter.interactorsRatio.isInfinite() ){
                counter.interactorsRatio = 0.0;
            }
            combinedResult.totalInteractors += counter.totalInteractors;
        }
        combinedResult.interactorsRatio = this.combinedResult.totalFound / speciesData.getEntitiesAndInteractorsCount().doubleValue();
        foundTotal = new MapSet<>();
    }

    public void setResultStatistics(Map<MainResource, Integer> sampleSizePerResource, Integer notFound, boolean includeInteractors){
        for (MainResource mainResource : this.getResources()) {
            Counter counter = this.entitiesResult.get(mainResource);
            counter.foundEntities = getEntitiesFound(mainResource);
            counter.foundReactions = getReactionsFound(mainResource);

            int found;
            if (includeInteractors) {
                counter.foundInteractors = getInteractorsFound(mainResource);
                found = getEntitiesAndInteractorsFound(mainResource); //Union of interactors and entities --> IMPORTANT
            } else {
                found = counter.foundEntities;
            }
            if (found > 0) {
                int sampleSize = sampleSizePerResource.get(mainResource) + notFound;
                double ratio = includeInteractors ? counter.interactorsRatio : counter.entitiesRatio;
                counter.entitiesPValue = MathUtilities.calculatePValue(ratio, sampleSize, found);
            }
        }

        Counter counter = combinedResult;
        counter.foundEntities = getEntitiesFound();
        int found;
        if(includeInteractors) {
            counter.foundInteractors = getInteractorsFound();
            found = getEntitiesAndInteractorsFound();
        } else {
            found = counter.foundEntities;
        }
        if( found > 0 ){
            Integer sampleSize = notFound;
            for (MainResource mainResource : sampleSizePerResource.keySet()) {
                sampleSize += sampleSizePerResource.get(mainResource);
            }
            double ratio = includeInteractors ? counter.interactorsRatio : counter.entitiesRatio;
            counter.entitiesPValue = MathUtilities.calculatePValue(ratio, sampleSize, counter.foundEntities);
        }
        counter.foundReactions = getReactionsFound();
    }

    protected Double getScore(){
        return getScore(this.combinedResult);
    }

    protected Double getScore(MainResource mainResource){
        return getScore(this.entitiesResult.get(mainResource));
    }

    private Double getScore(Counter counter){
        double entitiesPercentage = counter.foundEntities / counter.totalEntities.doubleValue();
        double reactionsPercentage = counter.foundReactions / counter.totalReactions.doubleValue();
        return (0.75 * (reactionsPercentage)) + (0.25 * (entitiesPercentage));
    }

    private Counter getOrCreateCounter(MainResource mainResource){
        Counter counter = this.entitiesResult.get(mainResource);
        if (counter == null) {
            counter = new Counter();
            this.entitiesResult.put(mainResource, counter);
        }
        return counter;
    }
}