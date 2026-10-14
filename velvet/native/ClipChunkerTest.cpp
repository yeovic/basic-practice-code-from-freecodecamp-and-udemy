#include "ClipChunker.hpp"
#include <cassert>
#include <iostream>

using namespace velvet_long_context;
int main(){
    assert(countChunks(0)==1);
    assert(countChunks(75)==1);
    assert(countChunks(76)==2);
    assert(countChunks(150)==2);
    assert(countChunks(151)==3);
    for(std::size_t n: {std::size_t(0),std::size_t(1),std::size_t(75),std::size_t(76),std::size_t(150),std::size_t(151),std::size_t(300)}) {
        IDs text(n);
        for(std::size_t i=0;i<n;++i)text[i]=static_cast<std::int32_t>(i+100);
        Chunks pieces=encodeChunks(text);
        assert(pieces.size()==countChunks(n));
        for(std::size_t i=0;i<n;++i)
            assert(pieces[i/75][1+i%75]==text[i]);
        for(auto &chunk: pieces){
            assert(chunk.size()==77);
            assert(chunk.front()==kBOS);
        }
        assert(pieces.back().back()==kEOS);
    }
    IDs positive(150,777),negative(20,666);
    auto paired=pair(positive,negative);
    assert(paired.rows==154);
    assert(paired.positive.size()==2 && paired.negative.size()==2);
    assert(paired.negative[1][0]==kBOS);
    assert(paired.negative[1][1]==kEOS);
    auto reverse=pair(IDs(4,222),IDs(150,333));
    assert(reverse.rows==154);
    assert(reverse.positive[1][1]==kEOS);
    bool rejected=false;
    try{encodeChunks(IDs(301,111));}
    catch(const std::length_error&){rejected=true;}
    assert(rejected);
    std::cout<<"PASS: exact BPE ID chunking 75/76/150/151, BOS-EOS padding, positive-negative CFG alignment, safe chunk budgets"<<std::endl;
}