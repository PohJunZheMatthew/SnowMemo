int amt = 100;
void main() throws IOException {
    for (int i = 0;i<amt;i++){
        File file = new File("/Users/polarbear1612/IdeaProjects/SnowMemo/memoSaves/demo"+i+".memo.json");
        if (!file.exists())file.createNewFile();
    }
}