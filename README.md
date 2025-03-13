### Installation

1. Clone the repository
```bash
git clone https://github.com/yourusername/Jamsy.git
cd Jamsy
```

2. Build the project
```bash
mvn clean install
```

3. Run the application
```bash
mvn spring-boot:run
```

4. How to merge code from main branch into developer branch
```bash
//Check current branch
git branch

//Switch to your branch
git checkout your-branch

//IMPORTANT!! Double check current branch!
git branch

//Fetch the latest changes
git fetch origin

//Merge main into your branch
git merge origin/main

//Push change from localhost to remote git repo
git push origin your-branch
```