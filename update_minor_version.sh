echo "Current version: $current_version"
major=${current_version%%.*}            
rest=${current_version#*.}               
minor=${rest%%.*}                        
patch=${rest#*.}                        
new_minor=$((minor + 1))
new_version="${major}.${new_minor}.0"
echo $new_version"
mvn versions:set -DnewVersion=$new_version
